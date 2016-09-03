/*
    This is an example application for transferring messages to and from the
    Simma Software VNA-Bluetooth. This application is meant only as an example
    of how to perform the basic required operations.
*/

package com.simmasoftware.vna_bt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VNA-BT";

    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 1;
    private static final UUID sppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final byte RS232_FLAG = (byte) 0xC0;
    private static final byte RS232_ESCAPE = (byte) 0xDB;
    private static final byte RS232_ESCAPE_FLAG = (byte) 0xDC;
    private static final byte RS232_ESCAPE_ESCAPE = (byte) 0xDD;
    private static final String DEGREE  = " \u00b0F";
    private static final int ACK = 0;
    private static final int FA_J1939 = 1;
    private static final int FD_J1939 = 2;
    private static final int FA_J1708 = 3;
    private static final int FD_J1708 = 4;
    private static final int TX_J1939 = 5;
    private static final int RX_J1939 = 6;
    private static final int TX_J1708 = 8;
    private static final int RX_J1708 = 9;
    private static final int VNA_MSG_GPS = 69;
    private static final int STATS = 23;
    private static final double KM_TO_MI = 0.621371;
    private static final double L_TO_GAL = 0.264172;
    private static final double KPA_TO_PSI = 0.145037738;
    private static final double KW_TO_HP = 1.34102209;
    private static final Integer MAX_16 = 0xffff;
    private static final Integer MAX_32 = 0xffffffff;
    private static final Integer MAX_8 = 0xff;
    private MenuItem connect_button = null;
    private boolean connected;
    private BluetoothSocket bluetoothSocket;
    private byte[] m_buffer;
    private int m_count;
    private boolean isInvalid;
    private boolean isStuffed;
    private int m_size;
    private HashMap<String, String> newData;
    private HashMap<String, Integer> monitorFields;

    double latInDegrees;
    double lonInDegrees;
    int noofSatellites;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initTextViews();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_connect) {
            connect_button = item;

            if(item.getTitle().toString().compareToIgnoreCase("Connect")==0)
            {
                // do BT connect
                item.setTitle("Connecting...");
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            }
            else
            {
                disconnect();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a bluetoothDevice to connect
                if (resultCode == Activity.RESULT_OK)
                {
                    final String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    Thread connectThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            connectDevice(address, 0);
                        }
                    });
                    connectThread.start();
                }
                break;
        }
    }

    private void initTextViews() {
        newData = new HashMap<>();
        monitorFields = new HashMap<>();

        newData.put("RPM", "");
        monitorFields.put("RPM", R.id.RPMField);
        newData.put("Coolant", "");
        monitorFields.put("Coolant", R.id.CoolantTempField);
        newData.put("Oil Pressure", "");
        monitorFields.put("Oil Pressure", R.id.OilPressureField);
        newData.put("Frames","");
        monitorFields.put("Frames", R.id.CANFramesField);
        newData.put("Latitude","");
        monitorFields.put("Latitude", R.id.LatitudeField);
        newData.put("Longitude","");
        monitorFields.put("Longitude", R.id.LongitudeField);
        newData.put("Satellites","");
        monitorFields.put("Satellites", R.id.SatellitesField);
    }

    private final Runnable readRun = new Runnable()
    {
        public void run()
        {
            receiveDataFromBT(bluetoothSocket);
        }
    };
    private Thread readThread;

    private BluetoothSocket connectDevice(String address, int i)
    {
        // Get the BluetoothDevice object

        BluetoothDevice bluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        connected = false;
        try
        {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(sppUUID);
            bluetoothSocket.connect();

            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_SHORT).show();
                    if (connect_button != null) connect_button.setTitle("Disconnect");
                    newData.put("Frames", "-");
                    updateLabels();
                }
            });
            connected = true;
            init_j1939();
            if(readThread != null && readThread.isAlive()) {
                readThread.interrupt();
                while(readThread.isAlive()) Thread.yield();
            } else {
                readThread = new Thread(readRun);
                readThread.setPriority(4);
                readThread.start();
            }

        }
        catch (Exception ioex)
        {
            Log.e(TAG, "", ioex);
        }

        if (!connected)
        {
            if(i<2){
                connectDevice(address, i+1);
            } else {
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Bluetooth connection error, try again", Toast.LENGTH_SHORT).show();
                        if (connect_button != null) connect_button.setTitle("Connect");
                        disconnect();
                    }
                });
            }
        }

        return (bluetoothSocket);
    }


    private void reconnect() {
        if(bluetoothSocket != null) {
            String address = bluetoothSocket.getRemoteDevice().getAddress();
            connected = false;
            try {
                bluetoothSocket.close();
            } catch (IOException e) { /* That's really too bad. */ }
            connectDevice(address, 1);
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth connection lost. Please reconnect.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void disconnect() {
        try
        {
            if(readThread != null) readThread.interrupt();
            if (bluetoothSocket != null)
            {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        }
        catch (IOException e)
        {
            /* We don't really care about the reconnect exceptions */
            Log.e(TAG, "In reconnect", e);
        }
        connected = false;
        if(connect_button != null) connect_button.setTitle("Connect");
    }

    private void receiveDataFromBT(BluetoothSocket socket) {
        try {
            byte[] buffer = new byte[1024];
            int buf_len = 0;


            if (socket == null) {
                return;
            }

            InputStream inputStream = socket.getInputStream();

            while (true) {
                try {
                    if(Thread.interrupted()) {
                        inputStream.close();
                        return;
                    }
                    // Read from the InputStream
                    if (inputStream != null && socket != null) {
                        buf_len = inputStream.read(buffer);
                    }
                    Thread.sleep(1);

                    if (buf_len == -1) {
                        inputStream.close();
                        break;
                    }
                    parseMessage(buffer, buf_len);

                } catch (IOException e) {
                    if(Thread.interrupted()) {
                        inputStream.close();
                        return;
                    }
                    reconnect();
                    break;
                } catch (InterruptedException e) {
                    inputStream.close();
                    Log.e(TAG,"Interrupted read",e);
                    return;
                }
            }

        } catch (IOException e) {
            Log.e(TAG,"", e);
        }
    }

    private void parseMessage(byte[] buf, int len) {
        for (int i = 0; i < len; i++) {
            processCharFromBus(buf[i]);
        }
    }

    private void processCharFromBus(byte val) {
        try {
            //Is it the start of the message?
            if (val == RS232_FLAG) {
                isInvalid = false;
                isStuffed = false;
                m_size = -1;
                m_count = 0;
            } else if (!isInvalid) {
                if (val == RS232_ESCAPE) {
                    isStuffed = true;
                } else {
                    //If previous byte was an escape, then decode current byte
                    if (isStuffed) {
                        isStuffed = false;
                        if (val == RS232_ESCAPE_FLAG) {
                            val = RS232_FLAG;
                        } else if (val == RS232_ESCAPE_ESCAPE) {
                            val = RS232_ESCAPE;
                        } else {
                            isInvalid = true;
                            // Invalid byte after escape, must abort
                            return;
                        }
                    }
                    //At this point data is always un-stuffed
                    if (m_count < m_buffer.length) {
                        m_buffer[m_count] = val;
                        m_count++;
                    } else {
                        //Full buffer
                    }

                    //At 2 bytes, we have enough info to calculate a real message length
                    if (m_count == 2) {
                        m_size = ((m_buffer[0] << 8) | m_buffer[1]) + 2;
                    }

                    //Have we received the entire message? If so, is it valid?
                    if (m_count == m_size && val == cksum(m_buffer, m_count -1)) {
                        m_count--; //Ignore the checksum at the end of the message
                        processPacket(m_buffer);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getStackTrace()[0]);
        }
    }

    private void processPacket(byte[] packet) {
        int msgID = packet[2];
        switch (msgID) {
            case RX_J1939:
                final Integer pgn = ((packet[4] & 0xFF) << 16) | ((packet[5] & 0xFF) << 8) | (packet[6] & 0xFF);
                Double d;
                Integer i;
                String out;
                switch (pgn) {
                    case 61444:
                        i = ((packet[14] & 0xFF) << 8) | (packet[13] & 0xFF);
                        if(i.equals(MAX_16)) break;
                        newData.put("RPM", (i * 0.125 + "")); /* SPN 190 */
                        break;
                    case 65262:
                        i = (packet[10] & 0xFF);
                        if(i.equals(MAX_8)) break;
                        d = (i - 40) * 9 / 5.0 + 32;
                        out = String.format("%.1f%s",d,DEGREE);
                        newData.put("Coolant",out); /* SPN 110 */
                        break;
                    case 65263:
                        i = (packet[13] & 0xFF);
                        if(i.equals(MAX_8)) break;
                        d = i * 4 * KPA_TO_PSI;
                        out = String.format("%.2f psi",d);
                        newData.put("Oil Pressure", out); /* SPN 100 */
                        break;
                }
                break;

            case STATS:
                Long canFramesCount = (long) (((packet[11] & 0xFF) << 24) | ((packet[12] & 0xFF) << 16) | ((packet[13] & 0xFF) << 8) | (packet[14] & 0xFF));
                newData.put("Frames", canFramesCount + " frames");
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateLabels();
                    }
                });
                break;

            case VNA_MSG_GPS:
                // Byte 2:          msgID
                // Byte 3:          lat degrees
                // Byte 4:          lat minutes
                // Byte 5,6,7:      lat decimal minutes
                // Byte 8:          N or S (negative if South)
                // Byte 9:          lon degrees
                // Byte 10:         lon minutes
                // Byte 11,12,13:   lon decimal minutes
                // Byte 14:         E or W (negative if West)
                // Byte 15:         Number of satellites in view

                // Latitude = Degrees + (Minutes + .minutes) / 60
                latInDegrees = ((packet[3] & 0xFF)
                        + (((packet[4] & 0xFF)
                        + ((double) (((packet[5] & 0xFF) << 16)
                        | ((packet[6] & 0xFF) << 8)
                        | (packet[7] & 0xFF)) / 100000)) / 60))
                        * (((packet[8] & 0xFF) == 'S') ? -1 : 1);
                newData.put("Latitude", String.format("%.6f", latInDegrees));

                // Longitude = Degrees + (Minutes + .minutes) / 60
                lonInDegrees = ((packet[9] & 0xFF)
                        + (((packet[10] & 0xFF)
                        + ((double) (((packet[11] & 0xFF) << 16)
                        | ((packet[12] & 0xFF) << 8)
                        | (packet[13] & 0xFF)) / 100000)) / 60))
                        * (((packet[14] & 0xFF) == 'W') ? -1 : 1);
                newData.put("Longitude", String.format("%.6f", lonInDegrees));

                noofSatellites = packet[15];
                newData.put("Satellites", noofSatellites + "");

                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateLabels();
                    }
                });
                break;
        }
    }

    private void updateLabels() {
        for(Fragment f : getSupportFragmentManager().getFragments()) {
            if(f != null && f.getClass().equals(MainActivityFragment.class)) {
                for (Map.Entry<String, Integer> entry : monitorFields.entrySet()) {
                    Integer tv = entry.getValue();
                    String label = newData.get(entry.getKey());
                    if (!label.equals("")) {
                        if (tv != null) {
                            ((MainActivityFragment)f).update(tv, label);
                        }
                    }
                }
            }
        }
    }

    private void sendCommand(TxStruct command)
    {
        String prefix = "";
        if (bluetoothSocket != null)
        {
            try
            {
                bluetoothSocket.getOutputStream().write(command.getBuf(),0,command.getLen());
            }
            catch (IOException e)
            {
                Log.e(TAG, "Send Command Socket Closed", e);
            }
        }
        else
        {
            disconnect();
        }
    }


    private int cksum(byte[] commandBytes)
    {
        int count = 0;

        for (int i = 1; i < commandBytes.length; i++)
        {
            count += uByte(commandBytes[i]);
        }

        return (byte) (~(count & 0xFF) + (byte) 1);
    }

    private int cksum(byte[] data, int numbytes) {
        int count = 0;

        for (int i = 0; i < numbytes; i++) {
            count += uByte(data[i]);
        }
        return (byte) (~(count & 0xFF) + (byte) 1);
    }

    private int uByte(byte b)
    {
        return (int)b & 0xFF;
    }

    private void init_j1939()
    {
        m_buffer = new byte[4096];
        m_count = 0;
        // trimUntil = 0;
        long[] initPGN_AddFilter = {61444, 65262, 65263};

        for(long pgn:initPGN_AddFilter)
        {
            sendCommand(filterAddDelJ1939((byte) 0, pgn, true));
        }
    }

    public TxStruct filterAddDelJ1939(byte port, long pgnLong, boolean add)
    {
        byte[] pgn = new byte[3];

        pgn[0] = (byte) ((pgnLong >> 16) & 0xFF);
        pgn[1] = (byte) ((pgnLong >> 8) & 0xFF);
        pgn[2] = (byte) ((pgnLong) & 0xFF);

        byte[] message = new byte[8];
        byte[] stuffed = new byte[17];
        int cnt;

        message[0] = 0;
        message[1] = 6;
        message[2] = (byte) (add ? FA_J1939 : FD_J1939);
        message[3] = port;
        System.arraycopy(pgn, 0, message, 4, 3);

        message[7] = (byte) cksum( message);


        // Tack on beginning of string marker

        stuffed[0] = RS232_FLAG;


        int esc_cnt = 1;

        // Bytestuff
        for( cnt = 0; cnt < 8; cnt++ )
        {
            if( message[cnt] == RS232_FLAG )
            {
                stuffed[cnt+esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt+esc_cnt] = RS232_ESCAPE_FLAG;
            }
            else if( message[cnt] == RS232_ESCAPE )
            {
                stuffed[cnt+esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt+esc_cnt] = RS232_ESCAPE_ESCAPE;
            }
            else
            {
                stuffed[cnt+esc_cnt] = message[cnt];
            }
        }
        return new TxStruct(stuffed, cnt+esc_cnt);
    }



    private TxStruct requestPGNJ1939(byte port, long pgnLong)
    {
        // c0 00 0a 05 00 pp gg nn 00 00 00 ff xx
        //                  PGN
        byte[] pgn = new byte[3];
        byte[] stuffed = new byte[30];

        pgn[0] = (byte) ((pgnLong) & 0xFF);
        pgn[1] = (byte) ((pgnLong >> 8) & 0xFF);
        pgn[2] = (byte) ((pgnLong >> 16) & 0xFF);

        byte[] message = new byte[14];
        int cnt;

        message[0] = 0;
        message[1] = (byte) (message.length - 2);
        message[2] = TX_J1939;
        message[3] = port;
        System.arraycopy(new byte[]{(byte) 0x00, (byte) 0xEA, (byte) 0x00}, 0, message, 4, 3);

        message[7] = (byte) 255; 	// destination addr
        message[8] = (byte) 252;				// source addr
        message[9] = 6;				// priority

        System.arraycopy(pgn, 0, message, 10, 3);

        message[13]	= (byte) cksum(message);

        // Tack on beginning of string marker
        stuffed[0] = RS232_FLAG;
        int esc_cnt = 1;
        // bytestuff
        for (cnt = 0; cnt < message.length; cnt++) {
            if (message[cnt] == RS232_FLAG) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_FLAG;
            } else if (message[cnt] == RS232_ESCAPE) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_ESCAPE;
            } else {
                stuffed[cnt + esc_cnt] = message[cnt];
            }
        }

        return new TxStruct(stuffed, cnt+esc_cnt);
    }

    class TxStruct {
        private byte[] buf;
        private int len;

        public TxStruct() {
            buf = new byte[10];
            len = 0;
        }

        public TxStruct(int bufSize, int len) {
            buf = new byte[bufSize];
            this.len = len;
        }

        public TxStruct(byte[] buf, int len) {
            this.buf = buf;
            this.len = len;
        }

        public void setLen(int length) {
            len = length;
        }

        public int getLen() {
            return len;
        }

        public void setBuf(int pos, byte data) {
            if( pos >= 0 && pos < buf.length ) buf[pos] = data;
        }

        public byte[] getBuf() {
            return buf;
        }
    }



}