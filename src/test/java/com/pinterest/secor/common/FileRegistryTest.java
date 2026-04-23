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
package com.pinterest.secor.common;

import com.pinterest.secor.io.FileWriter;
import com.pinterest.secor.util.FileUtil;
import com.pinterest.secor.util.ReflectionUtil;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.GzipCodec;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;

/**
 * FileRegistryTest tests the file registry logic.
 *
 * @author Pawel Garbacki (pawel@pinterest.com)
 */
public class FileRegistryTest {
    private static final String PATH = "/some_parent_dir/some_topic/some_partition/some_other_partition/"
            + "10_0_00000000000000000100";
    private static final String PATH_GZ = "/some_parent_dir/some_topic/some_partition/some_other_partition/"
            + "10_0_00000000000000000100.gz";
    private static final String CRC_PATH = "/some_parent_dir/some_topic/some_partition/some_other_partition/"
            + ".10_0_00000000000000000100.crc";
    private LogFilePath mLogFilePath;
    private LogFilePath mLogFilePathGz;
    private TopicPartition mTopicPartition;
    private FileRegistry mRegistry;

    @Before
    public void setUp() throws Exception {
        PropertiesConfiguration properties = new PropertiesConfiguration();
        properties.addProperty("secor.file.reader.writer.factory",
                "com.pinterest.secor.io.impl.SequenceFileReaderWriterFactory");
        properties.addProperty("secor.file.age.youngest", true);
        SecorConfig secorConfig = new SecorConfig(properties);
        mRegistry = new FileRegistry(secorConfig);
        mLogFilePath = new LogFilePath("/some_parent_dir", PATH);
        mTopicPartition = new TopicPartition("some_topic", 0);
        mLogFilePathGz = new LogFilePath("/some_parent_dir", PATH_GZ);
    }

    private FileWriter createWriter(MockedStatic<FileUtil> mockedFileUtil,
                                    MockedStatic<ReflectionUtil> mockedReflectionUtil) throws Exception {
        FileWriter writer = Mockito.mock(FileWriter.class);
        mockedReflectionUtil.when(() -> ReflectionUtil.createFileWriter(
                        anyString(),
                        any(LogFilePath.class),
                        any(),
                        any(SecorConfig.class)
                ))
                .thenReturn(writer);

        Mockito.when(writer.getLength()).thenReturn(123L);

        FileWriter createdWriter = mRegistry.getOrCreateWriter(
                mLogFilePath, null);
        assertSame(createdWriter, writer);

        return writer;
    }

    @Test
    public void testGetOrCreateWriter() throws Exception {
        try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class);
             MockedStatic<ReflectionUtil> mockedReflectionUtil = Mockito.mockStatic(ReflectionUtil.class)) {

            createWriter(mockedFileUtil, mockedReflectionUtil);

            // Call the method again. This time it should return an existing writer.
            mRegistry.getOrCreateWriter(mLogFilePath, null);

            // Verify that the method has been called exactly once (the default).
            mockedReflectionUtil.verify(() -> ReflectionUtil.createFileWriter(
                    anyString(),
                    any(LogFilePath.class),
                    any(),
                    any(SecorConfig.class)
            ));

            mockedFileUtil.verify(() -> FileUtil.delete(PATH));
            mockedFileUtil.verify(() -> FileUtil.delete(CRC_PATH));

            TopicPartition topicPartition = new TopicPartition("some_topic", 0);
            Collection<TopicPartition> topicPartitions = mRegistry
                    .getTopicPartitions();
            assertEquals(1, topicPartitions.size());
            assertTrue(topicPartitions.contains(topicPartition));

            Collection<LogFilePath> logFilePaths = mRegistry
                    .getPaths(topicPartition);
            assertEquals(1, logFilePaths.size());
            assertTrue(logFilePaths.contains(mLogFilePath));
        }
    }

    @Test
    public void testGetWriterShowBeNullForNewFilePaths() throws Exception {
        assertNull(mRegistry.getWriter(mLogFilePath));
    }

    @Test
    public void testGetWriterShowBeNotNull() throws Exception {
        try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class);
             MockedStatic<ReflectionUtil> mockedReflectionUtil = Mockito.mockStatic(ReflectionUtil.class)) {

            FileWriter createdWriter = createWriter(mockedFileUtil, mockedReflectionUtil);

            FileWriter writer = mRegistry.getWriter(mLogFilePath);
            assertNotNull(writer);
            assertEquals(createdWriter, writer);
        }
    }

    private FileWriter createCompressedWriter(MockedStatic<FileUtil> mockedFileUtil,
                                              MockedStatic<ReflectionUtil> mockedReflectionUtil) throws Exception {
        FileWriter writer = Mockito.mock(FileWriter.class);
        mockedReflectionUtil.when(() -> ReflectionUtil.createFileWriter(
                        anyString(),
                        any(LogFilePath.class),
                        any(),
                        any(SecorConfig.class)
                ))
                .thenReturn(writer);

        Mockito.when(writer.getLength()).thenReturn(123L);

        FileWriter createdWriter = mRegistry.getOrCreateWriter(
                mLogFilePathGz, new GzipCodec());
        assertSame(createdWriter, writer);

        return writer;
    }

    @Test
    public void testGetOrCreateWriterCompressed() throws Exception {
        try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class);
             MockedStatic<ReflectionUtil> mockedReflectionUtil = Mockito.mockStatic(ReflectionUtil.class)) {

            createCompressedWriter(mockedFileUtil, mockedReflectionUtil);

            mRegistry.getOrCreateWriter(mLogFilePathGz, new GzipCodec());

            // Verify that the method has been called exactly once (the default).
            mockedFileUtil.verify(() -> FileUtil.delete(PATH_GZ));
            mockedFileUtil.verify(() -> FileUtil.delete(CRC_PATH));

            mockedReflectionUtil.verify(() -> ReflectionUtil.createFileWriter(
                    anyString(),
                    any(LogFilePath.class),
                    any(),
                    any(SecorConfig.class)
            ));

            TopicPartition topicPartition = new TopicPartition("some_topic", 0);
            Collection<TopicPartition> topicPartitions = mRegistry
                    .getTopicPartitions();
            assertEquals(1, topicPartitions.size());
            assertTrue(topicPartitions.contains(topicPartition));

            Collection<LogFilePath> logFilePaths = mRegistry
                    .getPaths(topicPartition);
            assertEquals(1, logFilePaths.size());
            assertTrue(logFilePaths.contains(mLogFilePath));
        }
    }

    @Test
    public void testDeletePath() throws Exception {
        try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class);
             MockedStatic<ReflectionUtil> mockedReflectionUtil = Mockito.mockStatic(ReflectionUtil.class)) {

            createWriter(mockedFileUtil, mockedReflectionUtil);

            mRegistry.deletePath(mLogFilePath);
            mockedFileUtil.verify(() -> FileUtil.delete(PATH), Mockito.times(2));
            mockedFileUtil.verify(() -> FileUtil.delete(CRC_PATH), Mockito.times(2));

            assertTrue(mRegistry.getPaths(mTopicPartition).isEmpty());
            assertTrue(mRegistry.getTopicPartitions().isEmpty());
        }
    }

    @Test
    public void testDeleteTopicPartition() throws Exception {
        try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class);
             MockedStatic<ReflectionUtil> mockedReflectionUtil = Mockito.mockStatic(ReflectionUtil.class)) {

            createWriter(mockedFileUtil, mockedReflectionUtil);

            mRegistry.deleteTopicPartition(mTopicPartition);
            mockedFileUtil.verify(() -> FileUtil.delete(PATH), Mockito.times(2));
            mockedFileUtil.verify(() -> FileUtil.delete(CRC_PATH), Mockito.times(2));

            assertTrue(mRegistry.getTopicPartitions().isEmpty());
            assertTrue(mRegistry.getPaths(mTopicPartition).isEmpty());
        }
    }

    @Test
    public void testGetSize() throws Exception {
        try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class);
             MockedStatic<ReflectionUtil> mockedReflectionUtil = Mockito.mockStatic(ReflectionUtil.class)) {

            createWriter(mockedFileUtil, mockedReflectionUtil);

            assertEquals(123L, mRegistry.getSize(mTopicPartition));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetModificationAgeSec() throws Exception {
        try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class);
             MockedStatic<ReflectionUtil> mockedReflectionUtil = Mockito.mockStatic(ReflectionUtil.class)) {

            createWriter(mockedFileUtil, mockedReflectionUtil);

            // Use reflection to set a known creation time (90 seconds ago)
            Field creationTimesField = FileRegistry.class.getDeclaredField("mCreationTimes");
            creationTimesField.setAccessible(true);
            HashMap<LogFilePath, Long> creationTimes = (HashMap<LogFilePath, Long>) creationTimesField.get(mRegistry);
            long nowSec = System.currentTimeMillis() / 1000L;
            creationTimes.put(mLogFilePath, nowSec - 90);

            long ageSec = mRegistry.getModificationAgeSec(mTopicPartition);
            // Allow for small timing variance during test execution
            assertTrue("Age should be approximately 90 seconds, was: " + ageSec, ageSec >= 89 && ageSec <= 91);
        }
    }
}
