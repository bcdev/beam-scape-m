package org.esa.beam.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 26.11.13
 * Time: 17:13
 *
 * @author olafd
 */
public class ArrayIndexComparatorTest {

    @Test
    public void testCompare() throws Exception {

        Double[] values = new Double[]{4.0, 3.1, 6.9,
                                            5.5, 2.8, 0.9,
                                            5.2, 4.4, 3.7};
        ArrayIndexComparator comparator = new ArrayIndexComparator(values);
        Integer[] indexes = comparator.createIndexArray();
        Arrays.sort(indexes, comparator);
    // Now the indexes are in appropriate order:
        System.out.println();

    }
}
