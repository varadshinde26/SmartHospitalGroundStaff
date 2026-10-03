package com.smarthospital.util;
import java.security.*; import java.util.*; import javax.crypto.SecretKeyFactory; import javax.crypto.spec.PBEKeySpec;
public final class PasswordUtil {
 public static String hash(String password){ try { byte[] salt=new byte[16]; SecureRandom.getInstanceStrong().nextBytes(salt); byte[] dk=derive(password,salt,120000,256); return "PBKDF2$120000$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(dk); } catch(Exception e){ throw new IllegalStateException(e); } }
 public static boolean verify(String password,String stored){ try { String[] p=stored.split("\\$"); if(p.length!=4||!p[0].equals("PBKDF2")) return false; int it=Integer.parseInt(p[1]); byte[] salt=Base64.getDecoder().decode(p[2]); byte[] expected=Base64.getDecoder().decode(p[3]); byte[] actual=derive(password,salt,it,expected.length*8); return MessageDigest.isEqual(expected,actual); }catch(Exception e){return false;} }
 private static byte[] derive(String password,byte[] salt,int iterations,int bits)throws Exception{ PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,iterations,bits); try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();} }
}
