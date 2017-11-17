package au.gov.amsa.geo.adhoc;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import org.davidmoten.hilbert.HilbertCurve;
import org.davidmoten.hilbert.SmallHilbertCurve;

import au.gov.amsa.ais.rx.Streams;
import au.gov.amsa.risky.format.BinaryFixes;
import au.gov.amsa.risky.format.BinaryFixesFormat;

public class AdhocMain2 {

    public static void main(String[] args) throws IOException {

        File file = new File("/media/an/temp/out.fix");

        int choice = 2;
        if (choice == 1) {
            // write the file
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file, true))) {
                AtomicLong count = new AtomicLong();
                Streams.nmeaFromGzip("/media/an/temp/2017-11-16.txt.gz") //
                        .compose(x -> Streams.extractFixes(x)) //
                        .doOnNext(f -> {
                            long c = count.incrementAndGet();
                            if (c % 1000000 == 0) {
                                System.out.println("count=" + new DecimalFormat("0.###").format(c / 1000000.0) + "m");
                            }
                        }) //
                        .forEach(f -> BinaryFixes.write(f, os, BinaryFixesFormat.WITH_MMSI)); //
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (choice == 2) {
            // print out histogram (which turns out to be uniform surprisingly)
            // and files
            long minTime = BinaryFixes.from(file, false, BinaryFixesFormat.WITH_MMSI) //
                    .map(x -> x.time()).reduce((x, y) -> Math.min(x, y)).toBlocking().single();

            long maxTime = BinaryFixes.from(file, false, BinaryFixesFormat.WITH_MMSI) //
                    .map(x -> x.time()).reduce((x, y) -> Math.max(x, y)).toBlocking().single();
            System.out.println("start=" + new Date(minTime) + ", finish=" + new Date(maxTime));

            SmallHilbertCurve h = HilbertCurve.small().bits(16).dimensions(3);
            long maxIndexes = 1L << (16 * 3);

            int numPartitions = 800;
            int[] counts = new int[numPartitions];
            long step = maxIndexes / numPartitions;
            AtomicLong count = new AtomicLong();
            BinaryFixes.from(file, false, BinaryFixesFormat.WITH_MMSI) //
                    .doOnNext(f -> {
                        long c = count.incrementAndGet();
                        if (c % 1000000 == 0) {
                            System.out.println("count=" + new DecimalFormat("0.###").format(c / 1000000.0) + "m");
                        }
                    }) //
                    .forEach(fix -> {
                        long x = Math.round(Math.floor((fix.lat() + 90) / 180.0 * maxIndexes));
                        long y = Math.round(Math.floor((fix.lon() + 180) / 360.0 * maxIndexes));
                        long z = Math
                                .round(Math.floor((fix.time() - minTime) / ((double) maxTime - minTime) * maxIndexes));
                        long index = h.index(x, y, z);
                        int partition = (int) (index / step);
                        counts[partition]++;
                    });
            System.out.println("===============");
            System.out.println("== HISTOGRAM ==");
            System.out.println("===============");
            long sum = 0;
            for (int i = 0; i < numPartitions; i++) {
                if (counts[i] != 0) {
                    System.out.println(i + " -> " + counts[i]);
                    sum += counts[i];
                }
            }
            System.out.println("total=" + sum);
            System.out.println();
            System.out.println("===================");
            System.out.println("== WRITING FILES ==");
            System.out.println("===================");

            OutputStream[] outs = new OutputStream[numPartitions];
            for (int i = 0; i < outs.length; i++) {
                outs[i] = new BufferedOutputStream(
                        new FileOutputStream(new File("/media/an/temp/partition" + i + ".fix")));
            }
            ByteBuffer bb = BinaryFixes.createFixByteBuffer(BinaryFixesFormat.WITH_MMSI);
            BinaryFixes.from(file, false, BinaryFixesFormat.WITH_MMSI) //
                    .doOnNext(f -> {
                        long c = count.incrementAndGet();
                        if (c % 1000000 == 0) {
                            System.out.println("count=" + new DecimalFormat("0.###").format(c / 1000000.0) + "m");
                        }
                    }) //
                    .doOnNext(fix -> {
                        long x = Math.round(Math.floor((fix.lat() + 90) / 180.0 * maxIndexes));
                        long y = Math.round(Math.floor((fix.lon() + 180) / 360.0 * maxIndexes));
                        long z = Math
                                .round(Math.floor((fix.time() - minTime) / ((double) maxTime - minTime) * maxIndexes));
                        long index = h.index(x, y, z);
                        int partition = (int) (index / step);
                        BinaryFixes.write(fix, bb, BinaryFixesFormat.WITH_MMSI);
                        try {
                            outs[partition].write(bb.array());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnError(t -> t.printStackTrace()) //
                    .subscribe();
            
            for (int i = 0; i < outs.length; i++) {
                outs[i] .close();
            }

        }
    }

}