/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.pinterest.secor.io;

import java.io.File;
import java.nio.file.Files;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.hadoop.io.compress.GzipCodec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.pinterest.secor.common.LogFilePath;
import com.pinterest.secor.common.SecorConfig;
import com.pinterest.secor.util.ReflectionUtil;

import static org.junit.Assert.*;

/**
 * Test the file readers and writers using real file I/O
 *
 * @author Praveen Murugesan (praveen@uber.com)
 */
public class FileReaderWriterFactoryTest {

    private static final String BASENAME = "10_0_00000000000000000100";

    private LogFilePath mLogFilePath;
    private LogFilePath mLogFilePathGz;
    private SecorConfig mConfig;
    private File tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("secor-test").toFile();
        String dir = tempDir.getAbsolutePath() + "/some_topic/some_partition/some_other_partition";
        new File(dir).mkdirs();

        String path = dir + "/" + BASENAME;
        String pathGz = dir + "/" + BASENAME + ".gz";

        mLogFilePath = new LogFilePath(tempDir.getAbsolutePath(), path);
        mLogFilePathGz = new LogFilePath(tempDir.getAbsolutePath(), pathGz);
    }

    @After
    public void tearDown() throws Exception {
        // Clean up temp directory
        deleteRecursively(tempDir);
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private void setupSequenceFileReaderConfig() {
        PropertiesConfiguration properties = new PropertiesConfiguration();
        properties.addProperty("secor.file.reader.writer.factory",
                "com.pinterest.secor.io.impl.SequenceFileReaderWriterFactory");
        mConfig = new SecorConfig(properties);
    }

    private void setupDelimitedTextFileWriterConfig() {
        PropertiesConfiguration properties = new PropertiesConfiguration();
        properties.addProperty("secor.file.reader.writer.factory",
                "com.pinterest.secor.io.impl.DelimitedTextFileReaderWriterFactory");
        mConfig = new SecorConfig(properties);
    }

    @Test
    public void testSequenceFileWriter() throws Exception {
        setupSequenceFileReaderConfig();

        FileWriter writer = ReflectionUtil.createFileWriter(
                mConfig.getFileReaderWriterFactory(),
                mLogFilePath, null, mConfig);

        assertNotNull(writer);

        // Write some data
        writer.write(new KeyValue(100L, "test message".getBytes()));
        writer.close();

        // Verify file was created
        assertTrue(new File(mLogFilePath.getLogFilePath()).exists());
    }

    @Test
    public void testSequenceFileReader() throws Exception {
        setupSequenceFileReaderConfig();

        // First write a file
        FileWriter writer = ReflectionUtil.createFileWriter(
                mConfig.getFileReaderWriterFactory(),
                mLogFilePath, null, mConfig);
        writer.write(new KeyValue(100L, "test message".getBytes()));
        writer.close();

        // Then read it back
        FileReader reader = ReflectionUtil.createFileReader(
                mConfig.getFileReaderWriterFactory(),
                mLogFilePath, null, mConfig);

        assertNotNull(reader);

        KeyValue kv = reader.next();
        assertNotNull(kv);
        assertEquals(100L, kv.getOffset());
        assertArrayEquals("test message".getBytes(), kv.getValue());

        reader.close();
    }

    @Test
    public void testDelimitedTextFileWriter() throws Exception {
        setupDelimitedTextFileWriterConfig();

        FileWriter writer = ReflectionUtil.createFileWriter(
                mConfig.getFileReaderWriterFactory(),
                mLogFilePath, null, mConfig);

        assertNotNull(writer);
        assertEquals(0L, writer.getLength());

        writer.write(new KeyValue(100L, "test message".getBytes()));
        writer.close();

        // Verify file was created
        assertTrue(new File(mLogFilePath.getLogFilePath()).exists());
    }

    @Test
    public void testDelimitedTextFileReader() throws Exception {
        setupDelimitedTextFileWriterConfig();

        // First write a file
        FileWriter writer = ReflectionUtil.createFileWriter(
                mConfig.getFileReaderWriterFactory(),
                mLogFilePath, null, mConfig);
        writer.write(new KeyValue(100L, "test message".getBytes()));
        writer.close();

        // Then read it back
        FileReader reader = ReflectionUtil.createFileReader(
                mConfig.getFileReaderWriterFactory(),
                mLogFilePath, null, mConfig);

        assertNotNull(reader);

        KeyValue kv = reader.next();
        assertNotNull(kv);

        reader.close();
    }
}
