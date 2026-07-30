package net.mamby.health.testing;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Dependency-free provider hosted by the instrumentation APK.
 *
 * <p>The instrumentation and target APKs have different UIDs, so this provider is exported only
 * in the test manifest. Keeping the provider in Java avoids depending on libraries supplied by the
 * target APK when Android starts the test provider in its owning process.
 */
public final class TestDocumentProvider extends ContentProvider {
    public static final String AUTHORITY = "net.mamby.health.instrumented.documents";
    public static final String VALID_PDF = "valid-pdf";
    public static final String MISMATCHED_PDF = "mismatched-pdf";
    public static final String OVERSIZED_PDF = "oversized-pdf";
    public static final String UNAVAILABLE_PDF = "unavailable-pdf";

    private static final String TAG = "TestDocumentProvider";
    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_PNG = "image/png";
    private static final byte[] PDF_BYTES =
            "%PDF-1.4\n% synthetic test document\n".getBytes(StandardCharsets.US_ASCII);

    private static final PipeDataWriter<byte[]> PIPE_WRITER = new PipeDataWriter<>() {
        @Override
        public void writeDataToPipe(
                ParcelFileDescriptor output,
                Uri uri,
                String mimeType,
                Bundle options,
                byte[] bytes) {
            try (OutputStream stream = new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
                stream.write(bytes);
            } catch (IOException error) {
                Log.e(TAG, "Could not write synthetic document", error);
            }
        }
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getLastPathSegment();
        if (MISMATCHED_PDF.equals(path)) {
            return MIME_PNG;
        }
        if (VALID_PDF.equals(path)
                || OVERSIZED_PDF.equals(path)
                || UNAVAILABLE_PDF.equals(path)) {
            return MIME_PDF;
        }
        return null;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        String[] columns = projection != null
                ? projection
                : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] row = new Object[columns.length];
        String path = uri.getLastPathSegment();
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                row[index] = path + ".pdf";
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                row[index] = OVERSIZED_PDF.equals(path) ? Long.MAX_VALUE : (long) PDF_BYTES.length;
            }
        }
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String path = uri.getLastPathSegment();
        if (UNAVAILABLE_PDF.equals(path)) {
            throw new FileNotFoundException("Synthetic provider failure");
        }
        if (!VALID_PDF.equals(path)
                && !MISMATCHED_PDF.equals(path)
                && !OVERSIZED_PDF.equals(path)) {
            throw new FileNotFoundException(uri.toString());
        }
        return openPipeHelper(uri, MIME_PDF, null, PDF_BYTES, PIPE_WRITER);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only test provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
