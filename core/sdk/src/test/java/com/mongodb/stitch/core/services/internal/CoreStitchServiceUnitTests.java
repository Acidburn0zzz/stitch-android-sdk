/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.mongodb.stitch.core.auth.internal.StitchAuthRequestClient;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.net.Event;
import com.mongodb.stitch.core.internal.net.EventStream;
import com.mongodb.stitch.core.internal.net.Method;
import com.mongodb.stitch.core.internal.net.StitchAuthDocRequest;
import com.mongodb.stitch.core.internal.net.StitchAuthRequest;
import com.mongodb.stitch.core.internal.net.Stream;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.bson.codecs.Decoder;
import org.bson.codecs.IntegerCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.internal.Base64;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class CoreStitchServiceUnitTests {

  @Test
  public void testCallFunction() {
    final String serviceName = "svc1";
    final StitchServiceRoutes routes = new StitchServiceRoutes("foo");
    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
    final CoreStitchServiceClient coreStitchService =
        new CoreStitchServiceClientImpl(
            requestClient,
            routes,
            serviceName,
            BsonUtils.DEFAULT_CODEC_REGISTRY);

    doReturn(42)
        .when(requestClient)
        .doAuthenticatedRequest(
            any(StitchAuthRequest.class), ArgumentMatchers.<Decoder<Integer>>any());
    doReturn(42)
        .when(requestClient)
        .doAuthenticatedRequest(
            any(StitchAuthRequest.class),
            ArgumentMatchers.<Class<Integer>>any(),
            any(CodecRegistry.class));

    final String funcName = "myFunc1";
    final List<Integer> args = Arrays.asList(1, 2, 3);
    final Document expectedRequestDoc = new Document();
    expectedRequestDoc.put("name", funcName);
    expectedRequestDoc.put("service", serviceName);
    expectedRequestDoc.put("arguments", args);

    assertEquals(
        42, (int) coreStitchService.callFunction(
                funcName, args, null, new IntegerCodec()));
    assertEquals(42, (int) coreStitchService.callFunction(
            funcName, args, null, Integer.class));

    final ArgumentCaptor<StitchAuthDocRequest> reqArgument =
        ArgumentCaptor.forClass(StitchAuthDocRequest.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Decoder<Integer>> decArgument = ArgumentCaptor.forClass(Decoder.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Class<Integer>> clazzArgument = ArgumentCaptor.forClass(Class.class);

    verify(requestClient).doAuthenticatedRequest(reqArgument.capture(), decArgument.capture());
    assertEquals(reqArgument.getValue().getMethod(), Method.POST);
    assertEquals(reqArgument.getValue().getPath(), routes.getFunctionCallRoute());
    assertEquals(reqArgument.getValue().getDocument(), expectedRequestDoc);
    assertTrue(decArgument.getValue() instanceof IntegerCodec);

    verify(requestClient)
        .doAuthenticatedRequest(
            reqArgument.capture(),
            clazzArgument.capture(),
            any(CodecRegistry.class));
    assertEquals(reqArgument.getValue().getMethod(), Method.POST);
    assertEquals(reqArgument.getValue().getDocument(), expectedRequestDoc);
    assertEquals(reqArgument.getValue().getPath(), routes.getFunctionCallRoute());
    assertEquals(clazzArgument.getValue(), Integer.class);
  }

  @Test
  public void testStreamFunction() {
    final String serviceName = "svc1";
    final StitchServiceRoutes routes = new StitchServiceRoutes("foo");
    final StitchAuthRequestClient requestClient = Mockito.mock(StitchAuthRequestClient.class);
    final CoreStitchServiceClient coreStitchService =
        new CoreStitchServiceClientImpl(
            requestClient,
            routes,
            serviceName,
            BsonUtils.DEFAULT_CODEC_REGISTRY);

    final Stream<Integer> stream = new Stream<>(new EventStream() {
      @Override
      public Event nextEvent() {
        return new Event.Builder().withData("42").build();
      }

      @Override
      public boolean isOpen() {
        return false;
      }

      @Override
      public void close() {

      }

      @Override
      public void cancel() {

      }
    }, new IntegerCodec());

    doReturn(stream)
        .when(requestClient)
        .openAuthenticatedStream(
            any(StitchAuthRequest.class), ArgumentMatchers.<Decoder<Integer>>any());

    final String funcName = "myFunc1";
    final List<Integer> args = Arrays.asList(1, 2, 3);
    final Document expectedRequestDoc = new Document();
    expectedRequestDoc.put("name", funcName);
    expectedRequestDoc.put("service", serviceName);
    expectedRequestDoc.put("arguments", args);

    assertEquals(
        stream, coreStitchService.streamFunction(
            funcName, args, new IntegerCodec()));

    final ArgumentCaptor<StitchAuthRequest> reqArgument =
        ArgumentCaptor.forClass(StitchAuthRequest.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Decoder<Integer>> decArgument = ArgumentCaptor.forClass(Decoder.class);

    verify(requestClient).openAuthenticatedStream(reqArgument.capture(), decArgument.capture());
    assertEquals(reqArgument.getValue().getMethod(), Method.GET);
    assertEquals(reqArgument.getValue().getPath(),
        routes.getFunctionCallRoute() + "?stitch_request="
            + Base64.encode(expectedRequestDoc.toJson().getBytes(StandardCharsets.UTF_8)));
    assertTrue(decArgument.getValue() instanceof IntegerCodec);
    assertFalse(reqArgument.getValue().getUseRefreshToken());
  }
}
