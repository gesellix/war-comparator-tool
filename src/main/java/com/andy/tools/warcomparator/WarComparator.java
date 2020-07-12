package com.andy.tools.warcomparator;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;

import java.io.BufferedInputStream;
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

    new File(args[1]).delete();
    new File(args[3]).delete();

    Result result = isDifferent(args[0], args[1], args[2], args[3]);
    if (result.different) {
      System.out.println("Differences found");
      System.exit(1);
    }
    else {
      System.out.println("No significant differences found");
      System.exit(0);
    }
  }

  public static Result isDifferent(String newFile, String newFileExtractionFolder,
                                   String oldFile, String oldFileExtractionFolder) throws IOException {
    Result result = new Result();
    IgnoredJars ignoredJars = new IgnoredJars();
    FilenameOverride filenameOverride = new FilenameOverride();

    System.out.println("Ignoring META-INF files for war and jar files");

    Map<String, String> nwifs = extractChecksums(newFile, newFileExtractionFolder);
    Map<String, String> owifs = extractChecksums(oldFile, oldFileExtractionFolder);

    MapDifference<String, String> difference = Maps.difference(nwifs, owifs);
    if (!difference.areEqual()) {
      System.out.println("diffs in .war: " + difference.entriesDiffering());
      System.out.println("only in new war: " + difference.entriesOnlyOnLeft());
      System.out.println("only in old war: " + difference.entriesOnlyOnRight());
      result.different = true;
      result.reason.add("!Maps.difference(nwifs, owifs).areEqual()");
    }

    for (String file : nwifs.keySet()) {
      String oldFileOverride = filenameOverride.getOverride(file);

      // System.out.println(file+": "+ xx.get(file)+" - "+xx2.get(file));
      if (file.endsWith(".jar")
          && !ignoredJars.isIgnored(file)
          && !nwifs.get(file).equalsIgnoreCase(owifs.get(oldFileOverride))) {

        // 2 jars with different checksum

        Map<String, String> njifs = extractChecksums(newFileExtractionFolder + File.separator + file, null);
        Map<String, String> ojifs = extractChecksums(oldFileExtractionFolder + File.separator + oldFileOverride, null);

        MapDifference<String, String> jarDiff = Maps.difference(njifs, ojifs);
        if (!jarDiff.areEqual()) {
          System.out.println("Comparing jar: " + file + ", new: " + nwifs.get(file) + ", old: " + owifs.get(oldFileOverride));
          System.out.println("diffs in .jar: " + jarDiff.entriesDiffering());
          System.out.println("only in new jar: " + jarDiff.entriesOnlyOnLeft());
          System.out.println("only in old jar: " + jarDiff.entriesOnlyOnRight());
          result.different = true;
          result.reason.add("!Maps.difference(njifs, ojifs).areEqual()");
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
            if (includeInDiff(ze)) {
              files.put(ze.getName(), getMd5(newFile));
            }
          }
          else {
            if (includeInDiff(ze)) {
              files.put(ze.getName(), getMd5(zis));
            }
          }
        }
        ze = zis.getNextEntry();
      }

      zis.closeEntry();
      zis.close();

      return files;
    }
    catch (IOException ex) {
      System.err.println("error: " + ex.getMessage());
      throw ex;
    }
  }

  private static boolean includeInDiff(ZipEntry ze) {
    return !ze.getName().endsWith(".class")
           && !ze.getName().startsWith("META-INF/")
           && !ze.getName().matches("dependency\\..+\\.list")
           && !ze.getName().matches("properties\\..+\\.properties");
  }

  private static String getMd5(InputStream iis) throws IOException {
    return getMd5(new ByteSource() {
      @Override
      public InputStream openStream() {
        return new BufferedInputStream(iis) {
          @Override
          public void close() {
//            System.err.println("do not close");
          }
        };
      }
    });
  }

  private static String getMd5(File file) throws IOException {
    return getMd5(Files.asByteSource(file));
  }

  private static String getMd5(ByteSource byteSource) throws IOException {
    return byteSource.hash(Hashing.goodFastHash(256)).toString();
  }

  static class FilenameOverride {

    Map<String, String> overrides = new HashMap<>();

    FilenameOverride() {
    }

    String getOverride(String file) {
      if (overrides.containsKey(file)) {
        return overrides.get(file);
      }

      if (file.startsWith("classpath/")) {
        return file.substring("classpath/".length());
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
