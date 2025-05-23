/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309841
 * @summary Jarsigner should print a warning if an entry is removed
 * @library /lib/testlibrary
 */

import jdk.testlibrary.SecurityTools;
import jdk.testlibrary.JarUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class RemovedFiles {

    private static final String NONEXISTENT_ENTRIES_FOUND
            = "This jar contains signed entries for files that do not exist. See the -verbose output for more details.";

    public static void main(String[] args) throws Exception {
        JarUtils.createJarFile(
                Paths.get("a.jar"),
                Paths.get("."),
                Files.write(Paths.get("a"), "a".getBytes(StandardCharsets.UTF_8)),
                Files.write(Paths.get("b"), "b".getBytes(StandardCharsets.UTF_8)));
        SecurityTools.keytool("-genkeypair -storepass changeit -keystore ks -alias x -dname CN=x -keyalg RSA");
        SecurityTools.jarsigner("-storepass changeit -keystore ks a.jar x");

        // All is fine at the beginning.
        SecurityTools.jarsigner("-verify a.jar")
                .shouldNotContain(NONEXISTENT_ENTRIES_FOUND);

        // Remove an entry after signing. There will be a warning.
        JarUtils.deleteEntries(Paths.get("a.jar"), "a");
        SecurityTools.jarsigner("-verify a.jar")
                .shouldContain(NONEXISTENT_ENTRIES_FOUND);
        SecurityTools.jarsigner("-verify -verbose a.jar")
                .shouldContain(NONEXISTENT_ENTRIES_FOUND)
                .shouldContain("Warning: nonexistent signed entries: [a]");

        // Remove one more entry.
        JarUtils.deleteEntries(Paths.get("a.jar"), "b");
        SecurityTools.jarsigner("-verify a.jar")
                .shouldContain(NONEXISTENT_ENTRIES_FOUND);
        SecurityTools.jarsigner("-verify -verbose a.jar")
                .shouldContain(NONEXISTENT_ENTRIES_FOUND)
                .shouldContain("Warning: nonexistent signed entries: [a, b]");

        // Re-sign will not clear the warning.
        SecurityTools.jarsigner("-storepass changeit -keystore ks a.jar x");
        SecurityTools.jarsigner("-verify a.jar")
                .shouldContain(NONEXISTENT_ENTRIES_FOUND);

        // Unfortunately, if there is a non-file entry in manifest, there will be
        // a false alarm. See https://bugs.openjdk.org/browse/JDK-8334261.
        Manifest man = new Manifest();
        man.getMainAttributes().putValue("Manifest-Version", "1.0");
        man.getEntries().computeIfAbsent("Hello", key -> new Attributes())
                .putValue("Foo", "Bar");
        JarUtils.createJarFile(Paths.get("b.jar"),
                man,
                Paths.get("."),
                Paths.get("a"));
        SecurityTools.jarsigner("-storepass changeit -keystore ks b.jar x");
        SecurityTools.jarsigner("-verbose -verify b.jar")
                .shouldContain("Warning: nonexistent signed entries: [Hello]")
                .shouldContain(NONEXISTENT_ENTRIES_FOUND);

    }
}
