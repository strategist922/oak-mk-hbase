package ch.x42.terye.mk.hbase;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;

import ch.x42.terye.mk.hbase.HBaseMicroKernelSchema.JournalTable;

public class Journal {

    private static final int TIMEOUT = 1500;

    private HTable table;
    public LinkedHashSet<Long> revisionIds;
    private long headRevisionId;

    private Thread thread;
    private boolean done = false;
    private Object timeoutLock;
    private Object updateLock;

    public Journal(HTable table) throws IOException {
        this.table = table;
        this.revisionIds = new LinkedHashSet<Long>();
        this.revisionIds.add(0L);
        this.headRevisionId = 0L;
        this.timeoutLock = new Object();
        this.updateLock = new Object();

        // start update thread
        Updater updater = new Updater();
        updater.getNewestRevisionIds();
        thread = new Thread(updater);
        thread.setDaemon(true);
        thread.start();
    }

    public void update() {
        try {
            synchronized (updateLock) {
                synchronized (timeoutLock) {
                    timeoutLock.notify();
                }
                updateLock.wait();
            }
        } catch (InterruptedException e) {
            // thread has been notified
        }
    }

    public long getHeadRevisionId() {
        synchronized (revisionIds) {
            return headRevisionId;
        }
    }

    public LinkedList<Long> getRevisionIds() {
        synchronized (revisionIds) {
            return new LinkedList<Long>(revisionIds);
        }
    }

    public void addRevisionId(long revisionId) {
        synchronized (revisionIds) {
            revisionIds.add(revisionId);
            headRevisionId = revisionId;
        }
    }

    public void dispose() throws IOException {
        done = true;
        synchronized (timeoutLock) {
            timeoutLock.notify();
        }
        try {
            // wait for thread to die
            thread.join();
        } catch (InterruptedException e) {
            // ignore
        }
        table.close();
    }

    private class Updater implements Runnable {

        private void getNewestRevisionIds() throws IOException {
            Scan scan = new Scan();
            // XXX: don't scan full table every time
            ResultScanner scanner = table.getScanner(scan);
            Iterator<Result> iterator = scanner.iterator();
            while (iterator.hasNext()) {
                Result result = iterator.next();
                // discard uncommitted revisions
                if (!Bytes.toBoolean(result.getValue(
                        JournalTable.CF_DATA.toBytes(),
                        JournalTable.COL_COMMITTED.toBytes()))) {
                    continue;
                }
                long id = Bytes.toLong(result.getRow());
                synchronized (revisionIds) {
                    if (!revisionIds.contains(id)) {
                        revisionIds.add(id);
                        headRevisionId = id;
                    }
                }
            }
            scanner.close();
        }

        @Override
        public void run() {
            while (!done) {
                try {
                    getNewestRevisionIds();
                    synchronized (updateLock) {
                        updateLock.notify();
                    }
                    synchronized (timeoutLock) {
                        timeoutLock.wait(TIMEOUT);
                    }
                } catch (InterruptedException e) {
                    // thread has been interrupted
                } catch (TableNotFoundException e) {
                    // might happen if journal table is being dropped
                    return;
                } catch (DoNotRetryIOException e) {
                    // might happen if journal table is being dropped
                    return;
                } catch (IOException e) {
                    // XXX: log exception
                }
            }
        }
    }

}