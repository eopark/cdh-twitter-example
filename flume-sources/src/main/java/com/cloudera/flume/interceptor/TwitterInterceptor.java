/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.flume.interceptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.interceptor.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.stream.JsonReader;
import static com.cloudera.flume.interceptor.TwitterInterceptorConstants.*;

/**
 * Simple Interceptor class that sets the current system timestamp on all events
 * that are intercepted.
 * By convention, this timestamp header is named "timestamp" and its format
 * is a "stringified" long timestamp in milliseconds since the UNIX epoch.
 */
public class TwitterInterceptor implements Interceptor {
  private static final Logger logger =
    LoggerFactory.getLogger(TwitterInterceptor.class);

  private final boolean outputText;
  private final boolean outputUserName;
  private final boolean outputUserScreenName;

  /**
   * Only {@link TwitterInterceptor.Builder} can build me
   */
  private TwitterInterceptor(boolean outputText, boolean outputUserName, boolean outputUserScreenName) {
    this.outputText = outputText;
    this.outputUserName = outputUserName;
    this.outputUserScreenName = outputUserScreenName;
  }

  @Override
  public void initialize() {
    // no-op
  }

  /**
   * Modifies events in-place.
   */
  @Override
  public Event intercept(Event event) {
    Map<String, String> headers = new HashMap<String, String>(event.getHeaders());
    headers.put("SINKTYPE", "HBASE");
    byte[] body = readJsonStream(new ByteArrayInputStream(event.getBody()));
    return EventBuilder.withBody(body, headers);
  }

  private byte[] readJsonStream(InputStream is) {
    byte[] body = null;
    try {
      JsonReader reader = new JsonReader(new InputStreamReader(is, "UTF-8"));
      try {
        long id = 0L;
        String text = null;
        String userName = null;
        String userScreenName = null;
        reader.beginObject();
        while (reader.hasNext()) {
          String name = reader.nextName();
          if (name.equals("id")) {
            id = reader.nextLong();
          }
          else if (name.equals("text")) {
            text = reader.nextString();
          }
          else if (name.equals("user")) {
            reader.beginObject();
            while (reader.hasNext()) {
              name = reader.nextName();
              if (name.equals("name")) {
                userName = reader.nextString();
              }
              else if (name.equals("screen_name")) {
                userScreenName = reader.nextString();
              }
              else {
                reader.skipValue();
              }
            }
            reader.endObject();
          }
          else {
            reader.skipValue();
          }
        }
        reader.endObject();

        StringBuilder builder = new StringBuilder("____ID____:");
        builder.append(id);
        if (outputText) {
          builder.append("____TEXT____:").append(text);
        }
        if (outputUserName) {
          builder.append("____USER____:").append(userName);
        }
        if (outputUserScreenName) {
          builder.append("____SCREEN____:").append(userScreenName);
        }

        logger.debug("id: {}", id);
        logger.debug("text: {}", text);
        logger.debug("username: {}", userName);
        logger.debug("screenName: {}", userScreenName);

        body = builder.toString().getBytes("UTF-8");
      }
      finally {
        reader.close();
      }
    }
    catch (UnsupportedEncodingException e) {
      logger.error("UTF-8 is not supported on this runtime", e);
    }
    catch (IOException e) {
      logger.error("Caught an IOException", e);
    }
    return body;
  }

  /**
   * Delegates to {@link #intercept(Event)} in a loop.
   * @param events
   * @return
   */
  @Override
  public List<Event> intercept(List<Event> events) {
    List<Event> es = new ArrayList<Event>();
    for (Event event : events) {
      es.add(intercept(event));
      event.getHeaders().put("SINKTYPE", "HDFS");
      es.add(event);
    }
    return es;
  }

  @Override
  public void close() {
    // no-op
  }

  /**
   * Builder which builds new instances of the TwitterInterceptor.
   */
  public static class Builder implements Interceptor.Builder {

    private boolean outputText = OUTPUTTEXT_DEFAULT;
    private boolean outputUserName = OUTPUTUSERNAME_DEFAULT;
    private boolean outputUserScreenName = OUTPUTUSERSCREENNAME_DEFAULT;

    @Override
    public Interceptor build() {
      return new TwitterInterceptor(outputText, outputUserName, outputUserScreenName);
    }

    @Override
    public void configure(Context context) {
      outputText = context.getBoolean(OUTPUTTEXT, OUTPUTTEXT_DEFAULT);
      outputUserName = context.getBoolean(OUTPUTUSERNAME, OUTPUTUSERNAME_DEFAULT);
      outputUserScreenName = context.getBoolean(OUTPUTUSERSCREENNAME, OUTPUTUSERSCREENNAME_DEFAULT);
      StringBuilder builder = new StringBuilder("outputText: ");
      builder.append(outputText)
        .append(" outputUserName: ").append(outputUserName)
        .append(" outputUserScreenName: ").append(outputUserScreenName);
      logger.debug(builder.toString());
    }
  }
}
