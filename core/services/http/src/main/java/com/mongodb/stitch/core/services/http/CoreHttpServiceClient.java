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

package com.mongodb.stitch.core.services.http;

import com.mongodb.stitch.core.services.internal.CoreStitchService;
import java.util.Collections;
import org.bson.Document;

public class CoreHttpServiceClient {

  private final CoreStitchService service;

  protected CoreHttpServiceClient(final CoreStitchService service) {
    this.service = service;
  }

  protected HttpResponse executeInternal(final HttpRequest request) {

    final String action;
    switch (request.method) {
      case GET:
        action = RequestAction.GET_ACTION_NAME;
        break;
      case POST:
        action = RequestAction.POST_ACTION_NAME;
        break;
      case PUT:
        action = RequestAction.PUT_ACTION_NAME;
        break;
      case DELETE:
        action = RequestAction.DELETE_ACTION_NAME;
        break;
      case HEAD:
        action = RequestAction.HEAD_ACTION_NAME;
        break;
      case PATCH:
        action = RequestAction.PATCH_ACTION_NAME;
        break;
      default:
        throw new IllegalArgumentException(
            String.format("unknown method %s", request.method.toString()));
    }

    final Document args = new Document();
    args.put(RequestAction.HTTP_URL_PARAM, request.url);
    if (request.authUrl != null) {
      args.put(RequestAction.HTTP_AUTH_URL_PARAM, request.authUrl);
    }
    if (request.headers != null) {
      args.put(RequestAction.HTTP_HEADERS_PARAM, request.headers);
    }
    if (request.cookies != null) {
      args.put(RequestAction.HTTP_COOKIES_PARAM, request.cookies);
    }
    if (request.body != null) {
      args.put(RequestAction.HTTP_BODY_PARAM, request.body);
    }
    if (request.encodeBodyAsJson != null) {
      args.put(RequestAction.HTTP_ENCODE_BODY_AS_JSON_PARAM, request.encodeBodyAsJson);
    }
    if (request.form != null) {
      args.put(RequestAction.HTTP_FORM_PARAM, request.form);
    }
    if (request.followRedirects != null) {
      args.put(RequestAction.HTTP_FOLLOW_REDIRECTS_PARAM, request.followRedirects);
    }

    return service.callFunctionInternal(
        action,
        Collections.singletonList(args),
        HttpResponse.Decoder);
  }

  private static class RequestAction {
    static final String GET_ACTION_NAME = "get";
    static final String POST_ACTION_NAME = "post";
    static final String PUT_ACTION_NAME = "put";
    static final String DELETE_ACTION_NAME = "delete";
    static final String HEAD_ACTION_NAME = "head";
    static final String PATCH_ACTION_NAME = "patch";

    static final String HTTP_URL_PARAM = "url";
    static final String HTTP_AUTH_URL_PARAM = "authUrl";
    static final String HTTP_HEADERS_PARAM = "headers";
    static final String HTTP_COOKIES_PARAM = "cookies";
    static final String HTTP_BODY_PARAM = "body";
    static final String HTTP_ENCODE_BODY_AS_JSON_PARAM = "encodeBodyAsJSON";
    static final String HTTP_FORM_PARAM = "form";
    static final String HTTP_FOLLOW_REDIRECTS_PARAM = "followRedirects";
  }
}
