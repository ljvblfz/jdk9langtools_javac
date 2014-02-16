/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 7150368 8003412 8000407 8031545
 * @summary javac should include basic ability to generate native headers
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompareTest {
    public static void main(String... args) throws Exception {
        new CompareTest().run();
    }

    void run() throws Exception {
        File srcDir = new File(System.getProperty("test.src"));
        File classesDir = new File("classes");
        classesDir.mkdirs();
        File javacHeaders = new File("headers.javac");
        javacHeaders.mkdirs();
        File javahHeaders = new File("headers.javah");
        javahHeaders.mkdirs();

        List<String> javacArgs = new ArrayList<String>();
        javacArgs.add("-d");
        javacArgs.add(classesDir.getPath());
        javacArgs.add("-h");
        javacArgs.add(javacHeaders.getPath());
        javacArgs.add("-XDjavah:full");

        for (File f: srcDir.listFiles()) {
            if (f.getName().matches("TestClass[0-9]+\\.java")) {
                sourceFileCount++;
                javacArgs.add(f.getPath());
            }
        }

        int rc = com.sun.tools.javac.Main.compile(javacArgs.toArray(new String[javacArgs.size()]));
        if (rc != 0)
            throw new Exception("javac failed; rc=" + rc);

        List<String> javahArgs = new ArrayList<String>();
        javahArgs.add("-d");
        javahArgs.add(javahHeaders.getPath());

        for (File f: classesDir.listFiles()) {
            if (f.getName().endsWith(".class")) {
                javahArgs.add(inferBinaryName(f));
            }
        }

        PrintWriter pw = new PrintWriter(System.out, true);
        rc = com.sun.tools.javah.Main.run(javahArgs.toArray(new String[javahArgs.size()]), pw);
        if (rc != 0)
            throw new Exception("javah failed; rc=" + rc);

        compare(javahHeaders, javacHeaders);

        int javahHeaderCount = javahHeaders.list().length;
        int javacHeaderCount = javacHeaders.list().length;

        System.out.println(sourceFileCount + " .java files found");
        System.out.println(javacHeaderCount + " .h files generated by javac");
        System.out.println(javahHeaderCount + " .h files generated by javah");
        System.out.println(compareCount + " header files compared");

        if (javacHeaderCount != javahHeaderCount || javacHeaderCount != compareCount)
            error("inconsistent counts");

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    String inferBinaryName(File file) {
        String name = file.getName();
        return name.substring(0, name.length() - ".class".length()).replace("$", ".");
    }

    /** Compare two directories.
     *  @param f1 The golden directory
     *  @param f2 The directory to be compared
     */
    void compare(File f1, File f2) {
        compare(f1, f2, null);
    }

    /** Compare two files or directories
     *  @param f1 The golden directory
     *  @param f2 The directory to be compared
     *  @param p An optional path identifying a file within the two directories
     */
    void compare(File f1, File f2, String p) {
        File f1p = (p == null ? f1 : new File(f1, p));
        File f2p = (p == null ? f2 : new File(f2, p));
        if (f1p.isDirectory() && f2p.isDirectory()) {
            Set<String> children = new HashSet<String>();
            children.addAll(Arrays.asList(f1p.list()));
            children.addAll(Arrays.asList(f2p.list()));
            for (String c: children) {
                compare(f1, f2, new File(p, c).getPath()); // null-safe for p
            }
        }
        else if (f1p.isFile() && f2p.isFile()) {
            System.out.println("checking " + p);
            compareCount++;
            String s1 = read(f1p);
            String s2 = read(f2p);
            if (!s1.equals(s2)) {
                System.out.println("File: " + f1p + "\n" + s1);
                System.out.println("File: " + f2p + "\n" + s2);
                error("Files differ: " + f1p + " " + f2p);
            }
        }
        else if (f1p.exists() && !f2p.exists())
            error("Only in " + f1 + ": " + p);
        else if (f2p.exists() && !f1p.exists())
            error("Only in " + f2 + ": " + p);
        else
            error("Files differ: " + f1p + " " + f2p);
    }

    private String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            error("error reading " + f + ": " + e);
            return "";
        }
    }

    private void error(String msg) {
        System.out.println(msg);
        errors++;
    }

    private int errors;
    private int compareCount;
    private int sourceFileCount;
}
