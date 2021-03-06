package cgeo.contacts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.widget.Toast;

import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public final class ContactsActivity extends Activity {
    static final String LOG_TAG = "cgeo.contacts";

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }

        final String nickName = getParameter(uri, IContacts.PARAM_NAME);
        if (StringUtils.isEmpty(nickName)) {
            finish();
            return;
        }

        // search by nickname, exact
        List<Pair<Integer, String>> contacts = getContacts(nickName, ContactsContract.Data.CONTENT_URI, ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Nickname.NAME, false);

        // search by display name, exact
        if (contacts.isEmpty()) {
            contacts = getContacts(nickName, ContactsContract.Contacts.CONTENT_URI, BaseColumns._ID, ContactsContract.Contacts.DISPLAY_NAME, false);
        }

        // search by contained name parts
        if (contacts.isEmpty()) {
            contacts.addAll(getContacts(nickName, ContactsContract.Data.CONTENT_URI, ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Nickname.NAME, true));
            contacts.addAll(getContacts(nickName, ContactsContract.Contacts.CONTENT_URI, BaseColumns._ID, ContactsContract.Contacts.DISPLAY_NAME, true));
        }

        if (contacts.isEmpty()) {
            showToast(getString(R.string.contact_not_found, nickName));
            finish();
            return;
        }

        if (contacts.size() > 1) {
            selectContact(contacts);
        } else {
            final int contactId = contacts.get(0).first;
            openContactAndFinish(contactId);
        }
    }

    private void selectContact(@NonNull final List<Pair<Integer, String>> contacts) {
        final List<String> list = new ArrayList<>();
        for (final Pair<Integer, String> p : contacts) {
            list.add(p.second);
        }
        final CharSequence[] items = list.toArray(new CharSequence[list.size()]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.multiple_matches)
                .setItems(items, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final int contactId = contacts.get(which).first;
                        dialog.dismiss();
                        openContactAndFinish(contactId);
                    }
                })
                .setOnCancelListener(new OnCancelListener() {

                    @Override
                    public void onCancel(final DialogInterface dialog) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .create().show();
    }

    private void openContactAndFinish(final int id) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        final Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(id));
        intent.setData(uri);
        startActivity(intent);
        finish();
    }

    @NonNull
    private List<Pair<Integer, String>> getContacts(@NonNull final String searchName, final Uri uri, @NonNull final String idColumnName, @NonNull final String selectionColumnName, final boolean like) {
        final String[] projection = { idColumnName, selectionColumnName, ContactsContract.Contacts.DISPLAY_NAME };
        final String selection = selectionColumnName + (like ? " LIKE" : " =") + " ? COLLATE NOCASE";
        final String[] selectionArgs = { like ? "%" + searchName + "%" : searchName };
        Cursor cursor = null;

        final List<Pair<Integer, String>> result = new ArrayList<>();
        try {
            cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);
            while (cursor != null && cursor.moveToNext()) {
                final int foundId = cursor.getInt(0);
                final String foundName = cursor.getString(1);
                final String displayName = cursor.getString(2);
                result.add(new Pair<>(foundId, StringUtils.isNotEmpty(displayName) &&
                        !StringUtils.equalsIgnoreCase(foundName, displayName) ? foundName + " (" + displayName + ")" : foundName));
            }
        } catch (final Exception e) {
            Log.e(LOG_TAG, "ContactsActivity.getContactId", e);
        } finally {
            if (cursor != null) {
                cursor.close(); // no Closable Cursor below sdk 16
            }
        }
        return result;
    }

    public void showToast(final String text) {
        final Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);

        toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 100);
        toast.show();
    }

    @NonNull
    private static String getParameter(@NonNull final Uri uri, @NonNull final String paramKey) {
        try {
            final String param = uri.getQueryParameter(paramKey);
            if (param == null) {
                return StringUtils.EMPTY;
            }
            return URLDecoder.decode(param, CharEncoding.UTF_8).trim();
        } catch (final UnsupportedEncodingException e) {
            Log.e(LOG_TAG, "ContactsActivity.getParameter", e);
        }
        return StringUtils.EMPTY;
    }

}
