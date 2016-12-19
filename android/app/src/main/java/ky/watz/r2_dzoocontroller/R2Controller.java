package ky.watz.r2_dzoocontroller;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.AsyncTask;

import android.support.v7.app.AppCompatActivity;

import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.Intent;

import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ImageButton;

import android.app.ProgressDialog;

import java.io.IOException;
import java.io.OutputStream;

import java.util.UUID;

public class R2Controller extends AppCompatActivity {
    private final static boolean DEVELOPER_ALWAYS_CONNECT = false;
    // Widgets
    Button btnOn, btnOff, btnBlink, btnDisconnect, btnReset;
    ImageButton leftEngine, rightEngine;
    Switch circuit1, circuit2, circuit3, circuit4, engineLock;

    // Bluetooth shit
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    private OutputStream btOut;

    // Driving logic
    private boolean leftDown = false;
    private boolean rightDown = false;
    private boolean enginesLocked = false;

    // LED logic
    private boolean circuitDown[] = {false, false, false, false};

    // Intermediate command storage
    private short frontcomm = 0;
    private short moodcomm = 0;
    private short engcomm = 0;

    // Command masks
    private final static short FRONT_MASK = 0x0003;
    private final static short MOOD_MASK = 0x003C;
    private final static short ENG_MASK = 0x00C0;

    // General purpose UUID for connecting to HC05 boards.
    // Change to a generated one for connecting to anything else.
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent newint = getIntent();

        // Get address of the Bluetooth device to connect to.
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS);

        // Show controller layout
        setContentView(R.layout.activity_led_controller);

        // Call the widgtes
        btnOn = (Button)findViewById(R.id.buttonOn);
        btnOff = (Button)findViewById(R.id.buttonOff);
        btnBlink = (Button)findViewById(R.id.buttonBlink);
        btnDisconnect = (Button)findViewById(R.id.buttonDisconnect);
        btnReset = (Button)findViewById(R.id.buttonReset);
        circuit1 = (Switch)findViewById(R.id.switchCircuit1);
        circuit2 = (Switch)findViewById(R.id.switchCircuit2);
        circuit3 = (Switch)findViewById(R.id.switchCircuit3);
        circuit4 = (Switch)findViewById(R.id.switchCircuit4);
        leftEngine = (ImageButton)findViewById(R.id.buttonDriveLeft);
        rightEngine = (ImageButton)findViewById(R.id.buttonDriveRight);
        engineLock = (Switch)findViewById(R.id.driveSwitch);

        // Connect via bluetooth
        new ConnectBT().execute();

        // Listeners...

        btnOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                turnOnLed();
            }
        });

        btnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                turnOffLed();
            }
        });

        btnBlink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                blinkLed();
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               btRst();
            }
        });

        leftEngine.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!enginesLocked) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            engageLeftEngine();
                            break;
                        case MotionEvent.ACTION_UP:
                            disengageLeftEngine();
                            break;
                        default:
                            return false;
                    }
                    return true;
                } else {
                    return false;
                }
            }
        });

        rightEngine.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!enginesLocked) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            engageRightEngine();
                            break;
                        case MotionEvent.ACTION_UP:
                            disengageRightEngine();
                            break;
                        default:
                            return false;
                    }
                    return true;
                } else {
                    return false;
                }
            }
        });

        engineLock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    enginesLocked = true;
                    engageLeftEngine();
                    engageRightEngine();
                } else {
                    disengageLeftEngine();
                    disengageRightEngine();
                    enginesLocked = false;
                }
            }
        });

        circuit1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                circuitDown[0] = isChecked;
                String cmd = "MOOD 0";
                for (int i = 0; i < 4; i++) {
                    if (circuitDown[i]) {
                        cmd+= "," + (i + 1);
                    }
                }
                btMsg(cmd);
            }
        });

        circuit2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                circuitDown[1] = isChecked;
                String cmd = "MOOD 0";
                for (int i = 0; i < 4; i++) {
                    if (circuitDown[i]) {
                        cmd+= "," + (i + 1);
                    }
                }
                btMsg(cmd);
            }
        });

        circuit3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                circuitDown[2] = isChecked;
                String cmd = "MOOD 0";
                for (int i = 0; i < 4; i++) {
                    if (circuitDown[i]) {
                        cmd+= "," + (i + 1);
                    }
                }
                btMsg(cmd);
            }
        });

        circuit4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                circuitDown[3] = isChecked;
                String cmd = "MOOD 0";
                for (int i = 0; i < 4; i++) {
                    if (circuitDown[i]) {
                        cmd+= "," + (i + 1);
                    }
                }
                btMsg(cmd);
            }
        });
    }

    private void engageLeftEngine () {
        if (!leftDown) {
            leftDown = true;
            if (rightDown) {
                btMsg("DRIVE BOTH");
            } else {
                btMsg("DRIVE LEFT");
            }
            leftEngine.setAlpha((float) 0.2);
        }
    }

    private void disengageLeftEngine () {
        if (leftDown) {
            if (rightDown) {
                btMsg("DRIVE RIGHT");
            } else {
                btMsg("DRIVE OFF");
            }
            leftEngine.setAlpha((float) 1);
            leftDown = false;
        }
    }

    private void engageRightEngine () {
        if (!rightDown) {
            if (leftDown) {
                btMsg("DRIVE BOTH");
            } else {
                btMsg("DRIVE RIGHT");
            }
            rightDown = true;
            rightEngine.setAlpha((float) 0.2);
        }
    }

    private void disengageRightEngine () {
        if (rightDown) {
            if (leftDown) {
                btMsg("DRIVE LEFT");
            } else {
                btMsg("DRIVE OFF");
            }
            rightEngine.setAlpha((float) 1);
            rightDown = false;
        }
    }

    private void disconnect() {
        new AlertDialog.Builder(this)
                .setTitle("Disconnect")
                .setMessage("Are you sure you want to join the Dark Side?")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // If it is not busy
                        frontcomm = 0;
                        moodcomm = 0;
                        engcomm = 0;
                        btMsg("FLUSH");
                        if (btSocket != null) {
                            try {
                                // Close the connection
                                btSocket.close();
                            }
                            catch (IOException e) {
                                msg("Could not close Bluetooth Connection");
                                e.printStackTrace();
                            }
                        }
                        // Return to the first layout
                        finish();
                    }})
                .setNegativeButton(android.R.string.no, null).show();
    }

    private void turnOffLed() {
        // If the socket is open
        if (btSocket != null) {
            btMsg("FRONT OFF");
        }
    }

    private void turnOnLed() {
        // If the socket is open
        if (btSocket != null) {
            btMsg("FRONT ON");
        }
    }

    private void blinkLed() {
        // If the socket is open
        if (btSocket != null) {
            btMsg("FRONT BLINK");
        }
    }

    // fast way to call Toast
    private void msg(String s) {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    /*
     * Send a command to R2
     * Passed string commands are converted into R2-DZP and passed to the R2 unit
     *
     * String Format
     * Front LED
     *      FRONT OFF
     *      FRONT ON
     *      FRONT BLINK
     * Mood Lighting
     *      MOOD 0
     *      MOOD 1
     *      MOOD 1,2
     *      ...
     * Engines
     *      DRIVE OFF
     *      DRIVE LEFT
     *      DRIVE RIGHT
     *      DRIVE BOTH
     *
     * Ridiculous Two-Dimensional Zommunication Protocol (R2-DZP)
     * Front LED
     *      000000 00 (off)
     *      000000 01 (on)
     *      000000 10 (blink)
     * Mood Lighting
     *      00 0000 00 (off)
     *      00 0001 00 (Circuit 1 on)
     *      00 0010 00 (Circuit 2 on)
     *      00 0011 00 (Circuit 1,2 on)
     *      ...
     * Engines
     *      00 000000 (off)
     *      01 000000 (right on)
     *      10 000000 (left on)
     *      11 000000 (both on)
     * The three bytes are then combined to form the command message.
     */
    private void btMsg(String s) {
        if (!s.equals("FLUSH")) {
            try {
                String[] cmdSeq = s.split("\\s");
                if (cmdSeq.length != 2) {
                    System.out.print("cmdSeq [");
                    for (String c : cmdSeq) {
                        System.out.print("\"" + c + "\",");
                    }
                    System.out.println("]");
                    throw new Exception("Bad command (split length " + cmdSeq.length + "): " + s);
                }
                switch (cmdSeq[0].charAt(0)) {
                    case 'F':
                        // FRONT
                        if (cmdSeq[1].equals("OFF")) {
                            frontcomm = 0x00;
                        } else if (cmdSeq[1].equals("ON")) {
                            frontcomm = 0x01;
                        } else if (cmdSeq[1].equals("BLINK")) {
                            frontcomm = 0x02;
                        } else {
                            throw new Exception("Internal string to R2-DZP exception: illegal string command " + s);
                        }
                        break;
                    case 'M':
                        // MOOD
                        String[] circuits = cmdSeq[1].split("\\,");
                        moodcomm = 0x00;
                        for (int i = 0; i < circuits.length; i++) {
                            int cir = Integer.parseInt(circuits[i]);
                            if (cir < 0 || cir > 4) {
                                throw new Exception("Internal string to R2-DZP exception: illegal string command " + s);
                            } else if (cir != 0) {
                                moodcomm |= (0x2 << cir);
                            }
                        }
                        break;
                    case 'D':
                        // DRIVE
                        if (cmdSeq[1].equals("OFF")) {
                            engcomm = 0x00;
                        } else if (cmdSeq[1].equals("LEFT")) {
                            engcomm = 0x80;
                        } else if (cmdSeq[1].equals("RIGHT")) {
                            engcomm = 0x40;
                        } else if (cmdSeq[1].equals("BOTH")) {
                            engcomm = 0xC0;
                        } else {
                            throw new Exception("Internal string to R2-DZP exception: illegal string command " + s);
                        }
                        break;
                    default:
                        throw new Exception("Internal string to R2-DZP exception: illegal string command: " + s);
                }
            } catch (Exception e) {
                msg("An error occurred");
                e.printStackTrace();
                return;
            }
        }
        // combine the byte
        int cmdByte = (frontcomm & FRONT_MASK) | (moodcomm & MOOD_MASK) | (engcomm & ENG_MASK);
        System.out.println("[BT] >> " + Integer.toBinaryString(cmdByte));
        if (isBtConnected && !DEVELOPER_ALWAYS_CONNECT) {
            try {
                btOut.write(cmdByte);
                btOut.flush();
            } catch (IOException e) {
                msg("An error occurred");
                e.printStackTrace();
            }
        }
    }

    private void btRst() {
        new AlertDialog.Builder(this)
                .setTitle("R2 Reset")
                .setMessage("Are you sure you want to restart R2?")
                .setIcon(android.R.drawable.ic_lock_power_off)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (isBtConnected && !DEVELOPER_ALWAYS_CONNECT) {
                            try {
                                btOut.write(0xFF);
                                btOut.flush();
                                btOut.close();
                            } catch (IOException e) {
                                msg("An error occurred");
                                e.printStackTrace();
                            }
                        }
                        msg("R2-DZOO is Resetting...");
                        finish();
                    }
                })
                .setNegativeButton(android.R.string.no, null).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;
        private String[] startupMsg = {"Force-Choking Rebels...", "Eliminating Younglings...", "Growing Spare Hands...", "Connecting to the Force..."};

        @Override
        protected void onPreExecute() {
            // Show a progress dialogue with random favour text
            progress = ProgressDialog.show(R2Controller.this, "Charging Death Star", startupMsg[(int)Math.floor(Math.random() * 4.0)]);
        }

        @Override
        protected Void doInBackground(Void... devices) {
            // cCnnect in the background while the progress dialogue is going
            try {
                if (btSocket == null || !isBtConnected) {
                    // Get the mobile bluetooth device
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();

                    // Connects to the device's address and checks if it's available
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);

                    // Create a RFCOMM (SPP) connection
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

                    // Start connection
                    btSocket.connect();
                    btOut = btSocket.getOutputStream();
                }
            } catch (IOException e) {
                // Connection failed
                ConnectSuccess = false;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            progress.dismiss();

            // If we failed
            if (!ConnectSuccess && !DEVELOPER_ALWAYS_CONNECT) {
                msg("¯\\_(ツ)_/¯");
                finish();
            } else {
                msg("Force-Link established.");
                btMsg("FLUSH");
                isBtConnected = true;
            }
        }
    }
}
