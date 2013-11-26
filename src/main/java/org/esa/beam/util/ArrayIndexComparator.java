package org.esa.beam.util;

import java.util.Comparator;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 26.11.13
 * Time: 17:10
 *
 * @author olafd
 */
public class ArrayIndexComparator implements Comparator<Integer> {
    private final Double[] array;

    public ArrayIndexComparator(Double[] array)
    {
        this.array = array;
    }

    public Integer[] createIndexArray()
    {
        Integer[] indexes = new Integer[array.length];
        for (int i = 0; i < array.length; i++)
        {
            indexes[i] = i; // Autoboxing
        }
        return indexes;
    }

    @Override
    public int compare(Integer index1, Integer index2)
    {
        // Autounbox from Integer to int to use as array indexes
        return array[index1].compareTo(array[index2]);
    }
}

