package org.apache.iotdb.db.metadata.rocksdb;

import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.metadata.mnode.IMeasurementMNode;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.utils.FileUtils;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.iotdb.db.metadata.rocksdb.RocksDBReadWriteHandler.ROCKSDB_PATH;

public class MRocksDBUnitTest {

  private MRocksDBManager mRocksDBManager;

  @Before
  public void setUp() throws MetadataException {
    File file = new File(ROCKSDB_PATH);
    if (!file.exists()) {
      file.mkdirs();
    }
    mRocksDBManager = new MRocksDBManager();
  }

  @Test
  public void testStorageGroupOps() throws MetadataException, IOException, InterruptedException {
    List<PartialPath> storageGroups = new ArrayList<>();
    storageGroups.add(new PartialPath("root.sg1"));
    storageGroups.add(new PartialPath("root.inner.sg1"));
    storageGroups.add(new PartialPath("root.inner.sg2"));
    storageGroups.add(new PartialPath("root.inner1.inner2.inner3.sg"));
    storageGroups.add(new PartialPath("root.inner1.inner2.sg"));

    for (PartialPath sg : storageGroups) {
      mRocksDBManager.setStorageGroup(sg);
    }

    for (PartialPath sg : storageGroups) {
      mRocksDBManager.setTTL(sg, 200 * 10000);
    }

    mRocksDBManager.printScanAllKeys();

    Assert.assertTrue(mRocksDBManager.isPathExist(new PartialPath("root.sg1")));
    Assert.assertTrue(mRocksDBManager.isPathExist(new PartialPath("root.inner1.inner2.inner3")));
    Assert.assertFalse(mRocksDBManager.isPathExist(new PartialPath("root.inner1.inner5")));
    try {
      Assert.assertFalse(mRocksDBManager.isPathExist(new PartialPath("root.inner1...")));
    } catch (MetadataException e) {
      assert true;
    }

    Thread t1 =
        new Thread(
            () -> {
              try {
                List<PartialPath> toDelete = new ArrayList<>();
                toDelete.add(new PartialPath("root.sg1"));
                mRocksDBManager.deleteStorageGroups(toDelete);
              } catch (Exception e) {
                Assert.fail(e.getMessage());
              }
            });

    Thread t2 =
        new Thread(
            () -> {
              try {
                PartialPath path = new PartialPath("root.sg1.dd.m1");
                mRocksDBManager.createTimeseries(
                    path,
                    TSDataType.TEXT,
                    TSEncoding.PLAIN,
                    CompressionType.UNCOMPRESSED,
                    null,
                    null);
              } catch (Exception e) {
                Assert.fail(e.getMessage());
              }
            });

    t2.start();
    Thread.sleep(10);
    t1.start();
    Thread.sleep(10);

    PartialPath path = new PartialPath("root.sg1.dd.m2");
    Assert.assertThrows(
        MetadataException.class,
        () -> {
          mRocksDBManager.createTimeseries(
              path, TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED, null, null);
        });

    t1.join();
    t2.join();

    mRocksDBManager.printScanAllKeys();
  }

  @Test
  public void testCreateTimeSeries() throws MetadataException, IOException {
    PartialPath path = new PartialPath("root.tt.sg.dd.m1");
    mRocksDBManager.createTimeseries(
        path, TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED, null, null);

    IMeasurementMNode m1 = mRocksDBManager.getMeasurementMNode(path);
    Assert.assertNull(m1.getAlias());
    Assert.assertEquals(m1.getSchema().getCompressor(), CompressionType.UNCOMPRESSED);
    Assert.assertEquals(m1.getSchema().getEncodingType(), TSEncoding.PLAIN);
    Assert.assertEquals(m1.getSchema().getType(), TSDataType.TEXT);
    Assert.assertNull(m1.getSchema().getProps());

    PartialPath path2 = new PartialPath("root.tt.sg.dd.m2");
    mRocksDBManager.createTimeseries(
        path2, TSDataType.DOUBLE, TSEncoding.PLAIN, CompressionType.GZIP, null, "ma");
    IMeasurementMNode m2 = mRocksDBManager.getMeasurementMNode(path2);
    Assert.assertEquals(m2.getAlias(), "ma");
    Assert.assertEquals(m2.getSchema().getCompressor(), CompressionType.GZIP);
    Assert.assertEquals(m2.getSchema().getEncodingType(), TSEncoding.PLAIN);
    Assert.assertEquals(m2.getSchema().getType(), TSDataType.DOUBLE);
    Assert.assertNull(m2.getSchema().getProps());
  }

  @Test
  public void testCreateAlignedTimeSeries() throws MetadataException, IOException {
    PartialPath prefixPath = new PartialPath("root.tt.sg.dd");
    List<String> measurements = new ArrayList<>();
    List<TSDataType> dataTypes = new ArrayList<>();
    List<TSEncoding> encodings = new ArrayList<>();
    List<CompressionType> compressions = new ArrayList<>();

    for (int i = 0; i < 6; i++) {
      measurements.add("mm" + i);
      dataTypes.add(TSDataType.INT32);
      encodings.add(TSEncoding.PLAIN);
      compressions.add(CompressionType.UNCOMPRESSED);
    }
    mRocksDBManager.createAlignedTimeSeries(
        prefixPath, measurements, dataTypes, encodings, compressions);

    try {
      PartialPath path = new PartialPath("root.tt.sg.dd.mn");
      mRocksDBManager.createTimeseries(
          path, TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED, null, null);
      assert false;
    } catch (MetadataException e) {
      assert true;
    }
    mRocksDBManager.printScanAllKeys();
  }

  @Test
  public void testNodeTypeCount() throws MetadataException, IOException {
    List<PartialPath> storageGroups = new ArrayList<>();
    storageGroups.add(new PartialPath("root.sg1"));
    storageGroups.add(new PartialPath("root.inner.sg1"));
    storageGroups.add(new PartialPath("root.inner.sg2"));
    storageGroups.add(new PartialPath("root.inner1.inner2.inner3.sg"));
    storageGroups.add(new PartialPath("root.inner1.inner2.sg"));

    for (PartialPath sg : storageGroups) {
      mRocksDBManager.setStorageGroup(sg);
    }

    PartialPath path = new PartialPath("root.tt.sg.dd.m1");
    mRocksDBManager.createTimeseries(
        path, TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED, null, null);

    PartialPath path2 = new PartialPath("root.tt.sg.ddd.m2");
    mRocksDBManager.createTimeseries(
        path2, TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED, null, "ma");
    mRocksDBManager.printScanAllKeys();

    // test total series number
    Assert.assertEquals(2, mRocksDBManager.getTotalSeriesNumber());

    // test storage group number
    Assert.assertEquals(
        1,
        mRocksDBManager.getStorageGroupNum(new PartialPath("root.inner1.inner2.inner3.sg"), false));
    Assert.assertEquals(
        2, mRocksDBManager.getStorageGroupNum(new PartialPath("root.inner.**"), false));
    Assert.assertEquals(6, mRocksDBManager.getStorageGroupNum(new PartialPath("root.**"), false));

    // test all timeseries number
    Assert.assertEquals(
        1, mRocksDBManager.getAllTimeseriesCount(new PartialPath("root.tt.sg.dd.m1")));
    Assert.assertEquals(
        2, mRocksDBManager.getAllTimeseriesCount(new PartialPath("root.**"), false));

    // test device number
    Assert.assertEquals(0, mRocksDBManager.getDevicesNum(new PartialPath("root.inner1.inner2")));
    Assert.assertEquals(
        0, mRocksDBManager.getDevicesNum(new PartialPath("root.inner1.inner2.**"), false));
    Assert.assertEquals(2, mRocksDBManager.getDevicesNum(new PartialPath("root.tt.sg.**"), false));
    Assert.assertEquals(1, mRocksDBManager.getDevicesNum(new PartialPath("root.tt.sg.dd"), false));

    // todo wildcard

    // test nodes count in given level
    Assert.assertEquals(
        2, mRocksDBManager.getNodesCountInGivenLevel(new PartialPath("root.tt.sg"), 3, false));
  }

  @Test
  public void testPathPatternMatch() throws MetadataException, IOException {
    List<PartialPath> timeseries = new ArrayList<>();
    timeseries.add(new PartialPath("root.sg.d1.m1"));
    timeseries.add(new PartialPath("root.sg.d1.m2"));
    timeseries.add(new PartialPath("root.sg.d2.m1"));
    timeseries.add(new PartialPath("root.sg.d2.m2"));
    timeseries.add(new PartialPath("root.sg1.d1.m1"));
    timeseries.add(new PartialPath("root.sg1.d1.m2"));
    timeseries.add(new PartialPath("root.sg1.d2.m1"));
    timeseries.add(new PartialPath("root.sg1.d2.m2"));

    for (PartialPath path : timeseries) {
      mRocksDBManager.createTimeseries(
          path, TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED, null, null);
    }

    //    mRocksDBManager.traverseByPatternPath(new PartialPath("root.sg.d1.*"));
  }

  @Test
  public void testDeleteTimeseries() throws MetadataException, IOException {
    List<PartialPath> timeseries = new ArrayList<>();
    timeseries.add(new PartialPath("root.sg.d1.m1"));
    timeseries.add(new PartialPath("root.sg.d1.m2"));
    timeseries.add(new PartialPath("root.sg.d2.m1"));
    timeseries.add(new PartialPath("root.sg.d2.m2"));
    timeseries.add(new PartialPath("root.sg.d3.m1"));
    timeseries.add(new PartialPath("root.sg.d3.m2"));
    timeseries.add(new PartialPath("root.sg1.d1.m1"));
    timeseries.add(new PartialPath("root.sg1.d1.m2"));
    timeseries.add(new PartialPath("root.sg1.d2.m1"));
    timeseries.add(new PartialPath("root.sg1.d2.m2"));

    for (PartialPath path : timeseries) {
      mRocksDBManager.createTimeseries(
          path, TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED, null, null);
    }

    Assert.assertEquals(
        mRocksDBManager.getAllTimeseriesCount(new PartialPath("root.**")), timeseries.size());

    int count = timeseries.size();
    mRocksDBManager.deleteTimeseries(new PartialPath("root.sg.d1.*"));
    Assert.assertEquals(
        mRocksDBManager.getAllTimeseriesCount(new PartialPath("root.**")), count - 2);

    count = count - 2;
    mRocksDBManager.deleteTimeseries(new PartialPath("root.sg1.**"));
    Assert.assertEquals(
        mRocksDBManager.getAllTimeseriesCount(new PartialPath("root.**")), count - 4);

    count = count - 4;
    mRocksDBManager.deleteTimeseries(new PartialPath("root.sg.*.m1"));
    Assert.assertEquals(
        mRocksDBManager.getAllTimeseriesCount(new PartialPath("root.**")), count - 2);

    mRocksDBManager.printScanAllKeys();
  }

  @Test
  public void testUpsert() throws MetadataException, IOException {
    PartialPath path2 = new PartialPath("root.tt.sg.dd.m2");
    mRocksDBManager.createTimeseries(
        path2, TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED, null, "ma");

    IMeasurementMNode m1 = mRocksDBManager.getMeasurementMNode(new PartialPath("root.tt.sg.dd.m2"));
    Assert.assertEquals(m1.getAlias(), "ma");

    mRocksDBManager.changeAlias(new PartialPath("root.tt.sg.dd.m2"), "test");

    IMeasurementMNode m2 = mRocksDBManager.getMeasurementMNode(new PartialPath("root.tt.sg.dd.m2"));
    Assert.assertEquals(m2.getAlias(), "test");

    mRocksDBManager.printScanAllKeys();

    IMeasurementMNode m3 =
        mRocksDBManager.getMeasurementMNode(new PartialPath("root.tt.sg.dd.test"));
    Assert.assertEquals(m3.getAlias(), "test");
  }

  @After
  public void clean() {
    mRocksDBManager.close();
    resetEnv();
  }

  public void resetEnv() {
    File rockdDbFile = new File(ROCKSDB_PATH);
    if (rockdDbFile.exists() && rockdDbFile.isDirectory()) {
      FileUtils.deleteDirectory(rockdDbFile);
    }
  }
}
