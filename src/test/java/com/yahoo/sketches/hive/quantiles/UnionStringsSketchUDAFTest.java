/*
 * Copyright 2016, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */
package com.yahoo.sketches.hive.quantiles;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFParameterInfo;
import org.apache.hadoop.hive.ql.udf.generic.SimpleGenericUDAFParameterInfo;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator.Mode;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector.PrimitiveCategory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;

import com.yahoo.sketches.ArrayOfItemsSerDe;
import com.yahoo.sketches.ArrayOfStringsSerDe;
import com.yahoo.sketches.memory.NativeMemory;
import com.yahoo.sketches.quantiles.ItemsSketch;

import org.testng.annotations.Test;
import org.testng.Assert;

public class UnionStringsSketchUDAFTest {

  static final Comparator<String> comparator = Comparator.naturalOrder();
  static final ArrayOfItemsSerDe<String> serDe = new ArrayOfStringsSerDe();

  static final ObjectInspector binaryInspector =
    PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.BINARY);

  static final ObjectInspector intInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.INT);

  static final ObjectInspector structInspector = ObjectInspectorFactory.getStandardStructObjectInspector(
      Arrays.asList("a"),
      Arrays.asList(binaryInspector)
    );

  @Test(expectedExceptions = UDFArgumentException.class)
  public void getEvaluatorTooFewInspectors() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionStringsSketchUDAF().getEvaluator(info);
  }

  @Test(expectedExceptions = UDFArgumentException.class)
  public void getEvaluatorTooManyInspectors() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector, binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionStringsSketchUDAF().getEvaluator(info);
  }

  @Test(expectedExceptions = UDFArgumentTypeException.class)
  public void getEvaluatorWrongCategoryArg1() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { structInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionStringsSketchUDAF().getEvaluator(info);
  }

  @Test(expectedExceptions = UDFArgumentTypeException.class)
  public void getEvaluatorWrongTypeArg1() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { intInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionStringsSketchUDAF().getEvaluator(info);
  }

  @Test(expectedExceptions = UDFArgumentTypeException.class)
  public void getEvaluatorWrongCategoryArg2() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, structInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionStringsSketchUDAF().getEvaluator(info);
  }

  @Test(expectedExceptions = UDFArgumentTypeException.class)
  public void getEvaluatorWrongTypeArg2() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionStringsSketchUDAF().getEvaluator(info);
  }

  // PARTIAL1 mode (Map phase in Map-Reduce): iterate + terminatePartial
  @Test
  public void partial1ModeDefaultKDownsizeInput() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionStringsSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.PARTIAL1, inspectors);
    DataToDoublesSketchUDAFTest.checkResultInspector(resultInspector);

    @SuppressWarnings("unchecked")
    ItemsUnionState<String> state = (ItemsUnionState<String>) eval.getNewAggregationBuffer();

    ItemsSketch<String> sketch1 = ItemsSketch.getInstance(256, comparator);
    sketch1.update("a");
    eval.iterate(state, new Object[] { new BytesWritable(sketch1.toByteArray(serDe)) });

    ItemsSketch<String> sketch2 = ItemsSketch.getInstance(256, comparator);
    sketch2.update("b");
    eval.iterate(state, new Object[] { new BytesWritable(sketch2.toByteArray(serDe)) });

    BytesWritable bytes = (BytesWritable) eval.terminatePartial(state);
    ItemsSketch<String> resultSketch = ItemsSketch.getInstance(new NativeMemory(bytes.getBytes()), comparator, serDe);
    Assert.assertEquals(resultSketch.getK(), 128);
    Assert.assertEquals(resultSketch.getRetainedItems(), 2);
    Assert.assertEquals(resultSketch.getMinValue(), "a");
    Assert.assertEquals(resultSketch.getMaxValue(), "b");
    eval.close();
  }

  @Test
  public void partial1ModeGivenK() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionStringsSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.PARTIAL1, inspectors);
    DataToDoublesSketchUDAFTest.checkResultInspector(resultInspector);

    @SuppressWarnings("unchecked")
    ItemsUnionState<String> state = (ItemsUnionState<String>) eval.getNewAggregationBuffer();

    ItemsSketch<String> sketch1 = ItemsSketch.getInstance(256, comparator);
    sketch1.update("a");
    eval.iterate(state, new Object[] { new BytesWritable(sketch1.toByteArray(serDe)), new IntWritable(256) });

    ItemsSketch<String> sketch2 = ItemsSketch.getInstance(256, comparator);
    sketch2.update("b");
    eval.iterate(state, new Object[] { new BytesWritable(sketch2.toByteArray(serDe)), new IntWritable(256) });

    BytesWritable bytes = (BytesWritable) eval.terminatePartial(state);
    ItemsSketch<String> resultSketch = ItemsSketch.getInstance(new NativeMemory(bytes.getBytes()), comparator, serDe);
    Assert.assertEquals(resultSketch.getK(), 256);
    Assert.assertEquals(resultSketch.getRetainedItems(), 2);
    Assert.assertEquals(resultSketch.getMinValue(), "a");
    Assert.assertEquals(resultSketch.getMaxValue(), "b");
    eval.close();
  }

  // PARTIAL2 mode (Combine phase in Map-Reduce): merge + terminatePartial
  @Test
  public void partial2Mode() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionStringsSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.PARTIAL2, inspectors);
    DataToDoublesSketchUDAFTest.checkResultInspector(resultInspector);

    @SuppressWarnings("unchecked")
    ItemsUnionState<String> state = (ItemsUnionState<String>) eval.getNewAggregationBuffer();

    ItemsSketch<String> sketch1 = ItemsSketch.getInstance(256, comparator);
    sketch1.update("a");
    eval.merge(state, new BytesWritable(sketch1.toByteArray(serDe)));

    ItemsSketch<String> sketch2 = ItemsSketch.getInstance(256, comparator);
    sketch2.update("b");
    eval.merge(state, new BytesWritable(sketch2.toByteArray(serDe)));

    BytesWritable bytes = (BytesWritable) eval.terminatePartial(state);
    ItemsSketch<String> resultSketch = ItemsSketch.getInstance(new NativeMemory(bytes.getBytes()), comparator, serDe);
    Assert.assertEquals(resultSketch.getK(), 256);
    Assert.assertEquals(resultSketch.getRetainedItems(), 2);
    Assert.assertEquals(resultSketch.getMinValue(), "a");
    Assert.assertEquals(resultSketch.getMaxValue(), "b");
    eval.close();
  }

  // FINAL mode (Reduce phase in Map-Reduce): merge + terminate
  @Test
  public void finalMode() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionStringsSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.FINAL, inspectors);
    DataToDoublesSketchUDAFTest.checkResultInspector(resultInspector);

    @SuppressWarnings("unchecked")
    ItemsUnionState<String> state = (ItemsUnionState<String>) eval.getNewAggregationBuffer();

    ItemsSketch<String> sketch1 = ItemsSketch.getInstance(256, comparator);
    sketch1.update("a");
    eval.merge(state, new BytesWritable(sketch1.toByteArray(serDe)));

    ItemsSketch<String> sketch2 = ItemsSketch.getInstance(256, comparator);
    sketch2.update("b");
    eval.merge(state, new BytesWritable(sketch2.toByteArray(serDe)));

    BytesWritable bytes = (BytesWritable) eval.terminate(state);
    ItemsSketch<String> resultSketch = ItemsSketch.getInstance(new NativeMemory(bytes.getBytes()), comparator, serDe);
    Assert.assertEquals(resultSketch.getK(), 256);
    Assert.assertEquals(resultSketch.getRetainedItems(), 2);
    Assert.assertEquals(resultSketch.getMinValue(), "a");
    Assert.assertEquals(resultSketch.getMaxValue(), "b");
    eval.close();
  }

  // COMPLETE mode (single mode, alternative to MapReduce): iterate + terminate
  @Test
  public void complete1ModeDefaultK() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionStringsSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.COMPLETE, inspectors);
    DataToDoublesSketchUDAFTest.checkResultInspector(resultInspector);

    @SuppressWarnings("unchecked")
    ItemsUnionState<String> state = (ItemsUnionState<String>) eval.getNewAggregationBuffer();

    ItemsSketch<String> sketch1 = ItemsSketch.getInstance(comparator);
    sketch1.update("a");
    eval.iterate(state, new Object[] { new BytesWritable(sketch1.toByteArray(serDe)) });

    ItemsSketch<String> sketch2 = ItemsSketch.getInstance(comparator);
    sketch2.update("b");
    eval.iterate(state, new Object[] { new BytesWritable(sketch2.toByteArray(serDe)) });

    BytesWritable bytes = (BytesWritable) eval.terminate(state);
    ItemsSketch<String> resultSketch = ItemsSketch.getInstance(new NativeMemory(bytes.getBytes()), comparator, serDe);
    Assert.assertEquals(resultSketch.getK(), 128);
    Assert.assertEquals(resultSketch.getRetainedItems(), 2);
    Assert.assertEquals(resultSketch.getMinValue(), "a");
    Assert.assertEquals(resultSketch.getMaxValue(), "b");

    eval.reset(state);
    Assert.assertNull(eval.terminate(state));

    eval.close();
  }

}
