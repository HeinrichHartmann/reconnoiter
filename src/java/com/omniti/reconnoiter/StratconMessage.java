/*
 * Copyright (c) 2009, OmniTI Computer Consulting, Inc.
 * All rights reserved.
 * The software in this package is published under the terms of the GPL license
 * a copy of which can be found at:
 * https://labs.omniti.com/reconnoiter/trunk/src/java/LICENSE
 */

package com.omniti.reconnoiter;

import java.io.StringReader;
import java.util.HashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;
import com.omniti.reconnoiter.IEventHandler;
import com.omniti.reconnoiter.event.*;

public abstract class StratconMessage {
  // This is the text type in the noit_log.h
  private static final ThreadLocal < MessageDigest > md_tls = 
    new ThreadLocal < MessageDigest > () {
      @Override protected MessageDigest initialValue() {
        MessageDigest md;
        try { md = MessageDigest.getInstance("MD5"); }
        catch(java.security.NoSuchAlgorithmException nsae) {
          throw new RuntimeException(nsae);
        }
        return md;
      }
    };
  public final static String METRIC_STRING = "s";
  public final static HashMap<String,StratconMessageFactory> quicklookup = new HashMap<String,StratconMessageFactory>();
  protected byte[] signature;

  public static void ignorePrefix(String prefix) {
    quicklookup.put(prefix, null);
  }
  @SuppressWarnings("unchecked")
  public static boolean registerType(Class clazz) {
    boolean success = false;
    try {
      Method meth = clazz.getMethod("getPrefix");
      String prefix = (String)meth.invoke(clazz.newInstance());
      StratconMessageFactory smf = new StratconMessageFactory(clazz);
      quicklookup.put(prefix, smf);
      success = true;
    } catch (NoClassDefFoundError e) {
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    }
    return success;
  }  
  public static String[] parseToArray(String jlog, int num) {
    // Get rid of the null parameter
    String operand = jlog.endsWith("\n") ? jlog.substring(0, jlog.length()-1)
                                         : jlog;
    return operand.split("[\t]", num);
  }

  public String getPrefix() { return null; }

  protected long timeToLong(String time) {
    long ms = 0;
    int off = time.lastIndexOf('.');
    try {
      if(off == -1) {
        ms = Long.valueOf(time).longValue() * 1000;
      }
      else {
        ms = Long.valueOf(time.substring(0,off)).longValue() * 1000;
        ms = ms + Long.valueOf(time.substring(off+1)).longValue();
      }
    }
    catch (NumberFormatException e) {
      return 0;
    }
    return ms;
  }

  public int numparts() { return 0; }
  public byte[] signature() { return signature; }
  public abstract void handle(IEventHandler eh);

  public StratconMessage() {}
  // Check and make sure
  public StratconMessage(String[] parts) throws Exception {
    if (!parts[0].equals(this.getPrefix())) {
      throw new Exception("Incorrect state prefix:" + getPrefix() + " not applicable for " + getClass());
    }
    if (parts.length != numparts()) {
      throw new Exception("Incorrect amount of parts parsed, tossing message.");
    }

    MessageDigest md = md_tls.get();
    for(int i=0; i<parts.length;i++) {
      if(parts[i] != null) {
        byte[] piece = parts[i].getBytes();
        if(piece.length > 0) md.update(piece);
      }
    }
    signature = md.digest();
  }

  public static StratconMessage makeMessage(String jlog) {
    String[] parts;
    if(jlog == null || jlog.length() == 0) return null;
    int idxOftab = jlog.indexOf('\t');
    if(idxOftab < 0) return null;
    // The numbers of the parse are pulled from stratcon and
    // +1 for the extra remote
    try {
      String prefix = jlog.substring(0, idxOftab);

      StratconMessageFactory smf = quicklookup.get(prefix);
      if(smf == null) {
        return null;
      }
      parts = parseToArray(jlog, smf.numparts());
      return smf.make(parts);
    }
    catch(Exception e) {
      System.err.println("makeMessage: " + e);
      e.printStackTrace();
    }
    return null;
  }
  public static StratconMessage[] makeMessages(String big) {
    String[] lines = big.split("[\n]");
    StratconMessage[] messages = new StratconMessage[lines.length];
    for (int i = 0; i < messages.length; i++) {
      messages[i] = makeMessage(lines[i]);
    }
    return messages;
  }
  static {
    StratconMessage.registerType(NoitCheck.class);
    StratconMessage.registerType(NoitStatus.class);
    StratconMessage.registerType(NoitMetric.class);
    StratconMessage.registerType(NoitBundlev1.class);
    StratconMessage.registerType(NoitBundlev2.class);
    StratconMessage.registerType(StratconStatement.class);
    StratconMessage.registerType(StratconQuery.class);
    StratconMessage.registerType(StratconQueryStop.class);
    StratconMessage.ignorePrefix("n");
  }
}
