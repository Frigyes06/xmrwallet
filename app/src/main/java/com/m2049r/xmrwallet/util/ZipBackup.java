package com.m2049r.xmrwallet.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ZipBackup {
    final private Context context;
    final private String walletName;

    private ZipOutputStream zip;

    public void writeTo(Uri zipUri) throws IOException {
        if (zip != null)
            throw new IllegalStateException("zip already initialized");
        if (!isContentUri(zipUri))
            throw new IOException("Invalid backup URI");
        if (!Helper.isSafeWalletName(walletName))
            throw new IOException("Invalid wallet name");
        try {
            OutputStream output = context.getContentResolver().openOutputStream(zipUri);
            if (output == null) throw new IOException("Unable to open backup URI");
            zip = new ZipOutputStream(output);

            final File walletRoot = Helper.getWalletRoot(context);
            addFile(new File(walletRoot, walletName + ".keys"));
            addFile(new File(walletRoot, walletName));

            zip.close();
        } finally {
            if (zip != null) zip.close();
        }
    }

    private static boolean isContentUri(Uri uri) {
        return uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                && uri.getAuthority() != null && !uri.getAuthority().isEmpty();
    }

    private void addFile(File file) throws IOException {
        if (!file.exists()) return; // ignore missing files (e.g. the cache file might not exist)
        ZipEntry entry = new ZipEntry(file.getName());
        zip.putNextEntry(entry);
        writeFile(file);
        zip.closeEntry();
    }

    private void writeFile(File source) throws IOException {
        try (InputStream is = new FileInputStream(source)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                zip.write(buffer, 0, length);
            }
        }
    }

    private static final SimpleDateFormat DATETIME_FORMATTER =
            new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss");

    public String getBackupName() {
        return walletName + " " + DATETIME_FORMATTER.format(new Date());
    }
}
