/* (rank 110) copied from https://github.com/apache/jmeter/blob/222546caef3effb28e40906bc3a5e6d6104c16e8/src/core/src/test/java/org/apache/jmeter/resources/ResourceKeyUsageTest.java
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.resources;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jorphan.util.JOrphanUtils;
import org.junit.jupiter.api.Test;

public class ResourceKeyUsageTest {
    // We assume the test starts in "module" (e.g. src/core) directory (which is true for Gradle and IDEs)
    private static final File srcFiledir = new File("src/main/java");

    // Read resource into ResourceBundle and store in List
    private PropertyResourceBundle getRAS(String res) throws Exception {
        InputStream ras = this.getClass().getResourceAsStream(res);
        if (ras == null) {
            return null;
        }
        return new PropertyResourceBundle(ras);
    }

    // Check that calls to getResString use a valid property key name
    @Test
    public void checkResourceReferences() throws Exception {
        String resourceName = "/org/apache/jmeter/resources/messages.properties";
        PropertyResourceBundle messagePRB = getRAS(resourceName);
        assertNotNull("Resource bundle " + resourceName + " was not found", resourceName);
        List<String> failures = new ArrayList<>();

        PackageTest.findFile(srcFiledir, null, new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                final File file = new File(dir, name);
                // Look for calls to JMeterUtils.getResString()
                final Pattern pat = Pattern.compile(".*getResString\\(\"([^\"]+)\"\\).*");
                if (name.endsWith(".java")) {
                  BufferedReader fileReader = null;
                  try {
                    fileReader = new BufferedReader(new FileReader(file));
                    String s;
                    while ((s = fileReader.readLine()) != null) {
                        if (s.matches("\\s*//.*")) { // leading comment
                            continue;
                        }
                        Matcher m = pat.matcher(s);
                        if (m.matches()) {
                            final String key = m.group(1);
                            // Resource keys cannot contain spaces, and are forced to lower case
                            String resKey = key.replace(' ', '_'); // $NON-NLS-1$ // $NON-NLS-2$
                            resKey = resKey.toLowerCase(java.util.Locale.ENGLISH);
                            if (!key.equals(resKey)) {
                                System.out.println(file+": non-standard message key: '"+key+"'");
                            }
                            try {
                                messagePRB.getString(resKey);
                            } catch (MissingResourceException e) {
                                failures.add(file+": missing message key: '"+key+"'");
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    JOrphanUtils.closeQuietly(fileReader);
                }

                }
                return file.isDirectory();
            }
        });
        if (failures.isEmpty()) {
            return;
        }
        fail(String.join("\n", failures));
    }
}