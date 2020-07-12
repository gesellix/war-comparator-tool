package com.andy.tools.warcomparator;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WarComparator {

  public static void main(String[] args) throws IOException {
    if (args.length < 4) {
      System.err.println("Usage: WarComparator <newWar> <newWarExtractFolder> <oldWar> <oldWarExtractFolder>");
      System.err.println("newWar = New war file path");
      System.err.println("oldWar = New war file path");
      System.err.println("newWarExtractFolder = Temporary folder where new war files can be extracted for comparison");
      System.err.println("oldWarExtractFolder = Temporary folder where old war files can be extracted for comparison");
      System.exit(2);
    }
    Result result = isDifferent(args[0], args[1], args[2], args[3]);
    if (result.different) {
      System.out.println("Wars are different");
      System.exit(1);
    }
    else {
      System.out.println("Wars are similar");
      System.exit(0);
    }
  }

  public static Result isDifferent(String newFile, String newFileExtractionFolder,
                                   String oldFile, String oldFileExtractionFolder) throws IOException {
    Result result = new Result();
    IgnoredJars ignoredJars = new IgnoredJars();
    FilenameOverride filenameOverride = new FilenameOverride();

    Map<String, String> nwifs = extractChecksums(newFile, newFileExtractionFolder);
    Map<String, String> owifs = extractChecksums(oldFile, oldFileExtractionFolder);

    System.out.println("New: " + nwifs.size());
    System.out.println("Old: " + owifs.size());

    if (nwifs.size() != owifs.size()) {
      result.different = true;
      result.reason.add("nwifs.size() != owifs.size()");
    }

    for (String file : nwifs.keySet()) {
      if (file.startsWith("META-INF")) {
        System.out.println("Ignoring META-INF files for war: " + file);
        continue;
      }

      if (!owifs.containsKey(file)) {
        System.out.println("Old war does not contains new file " + file);
        result.different = true;
        result.reason.add("Old war does not contains new file " + file);
      }

      if (!file.endsWith(".jar") && !file.endsWith(".class") && !nwifs.get(file).equalsIgnoreCase(owifs.get(file))) {
        System.out.println("Diff file found " + file);
        result.different = true;
        result.reason.add("Diff file found " + file);
      }
    }

    System.out.println("WARS have same internals files, now comparing jars...");

    for (String file : nwifs.keySet()) {

      if (file.startsWith("META-INF")) {
        System.out.println("Ignoring META-INF files");
        continue;
      }

      String oldFileOverride = filenameOverride.getOverride(file);

      // System.out.println(file+": "+ xx.get(file)+" - "+xx2.get(file));
      if (file.endsWith(".jar") && !ignoredJars.isIgnored(file)) {
        // 2 jars with different checksum
        if (!nwifs.get(file).equals(owifs.get(oldFileOverride))) {
          System.out.println("Comparing jar: " + file + ", New: " + nwifs.get(file) + ", old: " + owifs.get(oldFileOverride));
        }

        if (!nwifs.get(file).equalsIgnoreCase(owifs.get(oldFileOverride))) {
          Map<String, String> njifs = extractChecksums(newFileExtractionFolder + File.separator + file, null);
          Map<String, String> ojifs = extractChecksums(oldFileExtractionFolder + File.separator + oldFileOverride, null);

          if (njifs.size() != ojifs.size()) {
            result.different = true;
            result.reason.add("njifs.size() != ojifs.size()");
          }

          for (String njif : njifs.keySet()) {
            if (njif.startsWith("META-INF")) {
              System.out.println("Ignoring META-INF files for jar: " + njif);
              continue;
            }

            if (!ojifs.containsKey(njif)) {
              System.out.println("File " + njif + " not found in old jar");
              result.different = true;
              result.reason.add("File " + njif + " not found in old jar");
            }

            if (!njif.endsWith(".class") && !njifs.get(njif).equalsIgnoreCase(ojifs.get(njif))) {
              System.out.println("Diff file found " + njif + " in jar");
              result.different = true;
              result.reason.add("Diff file found " + njif + " in jar");
            }
          }
        }
      }
    }

    return result;
  }

  public static Map<String, String> extractChecksums(String zipFile, String extractLibsFolder) throws IOException {
    Map<String, String> files = new HashMap<>();

    byte[] buffer = new byte[8192];

    try {
      ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));

      ZipEntry ze = zis.getNextEntry();

      while (ze != null) {

        if (!ze.isDirectory()) {

          if (ze.getName().endsWith(".jar")) {
            File newFile = new File(extractLibsFolder + File.separator + ze.getName());
            new File(newFile.getParent()).mkdirs();
            FileOutputStream fos = new FileOutputStream(newFile);

            int len;
            while ((len = zis.read(buffer)) > 0) {
              fos.write(buffer, 0, len);
            }

            fos.close();
            files.put(ze.getName(), getMd5(newFile));
          }
          else {
            files.put(ze.getName(), getMd5(zis));
          }
        }
        ze = zis.getNextEntry();
      }

      zis.closeEntry();
      zis.close();

      return files;
    }
    catch (IOException ex) {
      throw ex;
    }
  }

  private static String getMd5(InputStream iis) throws IOException {
    String str = DigestUtils.md5Hex(iis);
    return str;
  }

  private static String getMd5(File file) throws IOException {
    HashCode md5 = Files.hash(file, Hashing.md5());
    String md5Hex = md5.toString();
    return md5Hex;
  }

  static class FilenameOverride {

    Map<String, String> overrides = new HashMap<>();

    FilenameOverride() {
    }

    String getOverride(String file) {
      if (overrides.containsKey(file)) {
        return overrides.get(file);
      }

      return file;
    }
  }

  static class IgnoredJars {

    List<String> ignoredJars = new ArrayList<>();

    IgnoredJars() {
    }

    boolean isIgnored(String jarFile) {
      return ignoredJars.stream().anyMatch(jarFile::contains);
    }
  }

  static class Result {

    boolean different = false;
    List<String> reason = new ArrayList<>();
  }
}
