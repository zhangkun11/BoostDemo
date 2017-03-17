/*
 * Copyright 2013 Gerhard Klostermeier
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.example.admin.myapplication.nfc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.SparseArray;
import android.widget.Toast;

import com.example.ht380d_jar_demo.R;
import com.main.BaseApplication;

/**
 * Create a key map with the {@link KeyMapCreator} and then
 * read the tag.
 * @author Gerhard Klostermeier
 */
public class ReadTag extends Activity {

    private final static int KEY_MAP_CREATOR = 1;

    private Handler mHandler = new Handler();
    private SparseArray<String[]> mRawDump;

    /**
     * Check for external storage and show the {@link KeyMapCreator}.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_tag);

        if (!BaseApplication.isExternalStorageWritableErrorToast(this)) {
            finish();
            return;
        }

        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR,
                Environment.getExternalStoragePublicDirectory(
                        BaseApplication.HOME_DIR) + "/" + BaseApplication.KEYS_DIR);
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_read));
        startActivityForResult(intent, KEY_MAP_CREATOR);
    }

    /**
     * Checks the result code of the key mapping process. If the process
     * was successful the {@link #readTag()}
     * method will be called.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
        case KEY_MAP_CREATOR:
            if (resultCode != Activity.RESULT_OK) {
                // Error.
                if (resultCode == 4) {
                    // Error. Path from the calling intend was null.
                    // (This is really strange and should not occur.)
                    Toast.makeText(this, R.string.info_strange_error,
                            Toast.LENGTH_LONG).show();
                }
                finish();
            } else {
                // Read Tag.
                readTag();
            }
            break;
        }
    }

    /**
     * Triggered by {@link #onActivityResult(int, int, Intent)}
     * this method starts a worker thread that first reads the tag and then
     * calls {@link #createTagDump(SparseArray)}.
     */
    private void readTag() {
        final MCReader reader = BaseApplication.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Get key map from glob. variable.
                mRawDump = reader.readAsMuchAsPossible(
                        BaseApplication.getKeyMap());

                reader.close();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        createTagDump(mRawDump);
                    }
                });
            }
        }).start();
    }

    /**
     * Create a tag dump in a format the {@link DumpEditor}
     * can read (format: headers (sectors) marked with "+", errors
     * marked with "*"), and then start the dump editor with this dump.
     * @param rawDump A tag dump like {@link MCReader#readAsMuchAsPossible()}
     * returns.
     * @see DumpEditor#EXTRA_DUMP
     * @see DumpEditor
     */
    private void createTagDump(SparseArray<String[]> rawDump) {
        String dump = "";
        String s = System.getProperty("line.separator");
        if (rawDump != null) {
            if (rawDump.size() != 0) {
                for (int i = BaseApplication.getKeyMapRangeFrom();
                        i <= BaseApplication.getKeyMapRangeTo(); i++) {
                    String[] val = rawDump.get(i);
                    // Mark headers (sectors) with "+".
                    dump += "+Sector: " + i + s;
                    if (val != null ) {
                        for (int j = 0; j < val.length; j++) {
                            dump += val[j] + s;
                        }
                    } else {
                        // Mark sector as not readable ("*").
                        dump += "*No keys found or dead sector" + s;
                    }
                }
                // UGLY: remove last "\n".
                dump = dump.substring(0, dump.length() -1);
                // Show Dump Editor Activity.
                Intent intent = new Intent(this,
                        DumpEditor.class);;
                intent.putExtra(DumpEditor.EXTRA_DUMP, dump);
                startActivity(intent);
            } else {
                // Error, keys from key map are not valid for reading.
                Toast.makeText(this, R.string.info_none_key_valid_for_reading,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.info_tag_removed_while_reading,
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
