package cn.com.warlock.emitter;

import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import cn.com.warlock.emitter.GeneratorException;
import cn.com.warlock.emitter.IDGenerator;
import cn.com.warlock.emitter.LocalUniqueIDGeneratorFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class UniqueIDGeneratorThreadSafetyIT {

    @Test
    public void multipleInstancesTest() throws InterruptedException {
        final Set<String> ids = Collections.synchronizedSet(new HashSet<String>());
        final int threadCount = 20;
        final int iterationCount = 10000;
        final CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    IDGenerator generator = LocalUniqueIDGeneratorFactory.generatorFor(1, 1);
                    try {
                        for (int i = 0; i < iterationCount; i++) {
                            byte[] id = generator.generate();
                            String asHex = Hex.encodeHexString(id);
                            ids.add(asHex);
                        }
                    } catch (GeneratorException e) {
                        e.printStackTrace();
                    }
                    latch.countDown();
                }
            });
            t.start();
        }

        boolean successfullyUnlatched = latch.await(20, TimeUnit.SECONDS);
        assertThat(successfullyUnlatched, is(true));

        assertThat(ids.size(), is(threadCount * iterationCount));
    }

    @Test
    public void moreThanOneGeneratorClusterIDTest() throws InterruptedException {
        final Set<String> ids = Collections.synchronizedSet(new HashSet<String>());
        final int[][] profiles = { { 0, 0 }, { 1, 1 }, { 1, 2 }, { 1, 3 }, { 1, 15 }, { 2, 0 },
                                   { 3, 0 }, { 4, 0 }, { 5, 0 }, { 63, 0 } };
        final int iterationCount = 10000;
        final CountDownLatch latch = new CountDownLatch(profiles.length);

        for (final int[] profile : profiles) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    IDGenerator generator = LocalUniqueIDGeneratorFactory.generatorFor(profile[0],
                        profile[1]);
                    try {
                        for (int i = 0; i < iterationCount; i++) {
                            byte[] id = generator.generate();
                            String asHex = Hex.encodeHexString(id);
                            ids.add(asHex);
                        }
                    } catch (GeneratorException e) {
                        e.printStackTrace();
                    }
                    latch.countDown();
                }
            });
            t.start();
        }

        boolean successfullyUnlatched = latch.await(20, TimeUnit.SECONDS);
        assertThat(successfullyUnlatched, is(true));

        assertThat(ids.size(), is(profiles.length * iterationCount));
    }
}
