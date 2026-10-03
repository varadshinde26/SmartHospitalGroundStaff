package com.smarthospital.util;
public final class Json { public static String esc(String s){if(s==null)return "";return s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","").replace("\n","\\n");} }
