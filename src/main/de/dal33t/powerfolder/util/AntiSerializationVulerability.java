/*
 * Copyright 2004 - 2015 Christian Sprajc. All rights reserved.
 *
 * This file is part of PowerFolder.
 *
 * PowerFolder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 *
 * PowerFolder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id: Util.java 20555 2012-12-25 04:15:08Z glasgow $
 */
package de.dal33t.powerfolder.util;

/**
 * PFS-1858: Prevent possible attack through Apache commons collections /
 * InvokerTransformer
 * 
 * @author sprajc
 */
public class AntiSerializationVulerability {

    private static final String[] CLASSES = new String[]{
        "org.apache.commons.collections.functors.InvokerTransformer"};

    private AntiSerializationVulerability() {
    }

    public static void check() {
        for (int i = 0; i < CLASSES.length; i++) {
            String className = CLASSES[i];
            try {
                Class.forName(className);
                String msg = "Found potential vulnerable class " + className
                    + ". Please remove it from classpath.";
                System.err.println(msg);
                System.exit(99);
            } catch (ClassNotFoundException e) {
                // OK!
            }
        }
    }
}
