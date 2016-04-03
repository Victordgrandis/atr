package com.atr.arduino.lentes;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends Activity {
    // App Variables
    TextView Conectado, Distancia;
    Button conectarButton, desconectarButton;
    ArrayList<String> Datos;
    Float promedio, error;
    String distancia;
    public ListView mList;
    boolean listo= true;
    // Sonido
    SoundPool soundPool;
    int soundID;
    // Bluetooth variables
    BluetoothAdapter btAdapter;
    BluetoothSocket btSocket;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread, alertaThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String address = "30:14:06:26:01:02";
    private static final String tag = "MainActivity";
    public static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Conectado = (TextView) findViewById(R.id.conectado);
        Distancia = (TextView) findViewById(R.id.distancia);
        conectarButton = (Button) findViewById(R.id.conectar);
        desconectarButton = (Button) findViewById(R.id.desconectar);
        mList = (ListView) findViewById(R.id.list);
        Datos = new ArrayList<>();
        inicializarSonido();

        try
        {
            findBT();
            openBT();
        } catch (IOException ex) {
            Log.e(tag, "Error al conectar dispositivo bluetooth");
        }

        conectarButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startVoiceRecognitionActivity();
/*
                try {
                    findBT();
                    openBT();
                } catch (IOException ex) {
                    Log.e(tag, "Error al conectar dispositivo bluetooth");
                }
*/
            }
        });

        desconectarButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                try {
                    closeBT();
                } catch (IOException ex) {
                    Log.e(tag, "Error al desconectar dispositivo bluetooth");
                }

            }
        });
    }


    private void findBT() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
        }

        if (!btAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }
    }

    private void openBT() throws IOException {
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        btSocket = device.createInsecureRfcommSocketToServiceRecord(uuid);
        btSocket.connect();
        mmOutputStream = btSocket.getOutputStream();
        mmInputStream = btSocket.getInputStream();
        Conectado.setText(R.string.conectado);
        Conectado.setTextColor(getResources().getColor(R.color.green));
        escucharDatos();
        alertar();
    }

    private void escucharDatos() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        public void run() {
                                            almacenarDatos(data);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }
    void alertar(){
        final int[] cont = {0};
        final Float[] dato = {Float.valueOf(0)};
        alertaThread = new Thread(new Runnable() {
            public void run() {
                while(!Thread.currentThread().isInterrupted() && btSocket.isConnected()) {
                    try {
                        alertaProximidad();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        alertaThread.start();
    }

    void closeBT() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        btSocket.close();
        Conectado.setText(R.string.desconectado);
        Conectado.setTextColor(getResources().getColor(R.color.red));
    }

    void inicializarSonido() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(audioAttributes)
                .build();
        soundID = soundPool.load(this, R.raw.beep, 1);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                listo = true;
            }
        });
    }

    @SuppressLint("SetTextI18n")
    void almacenarDatos(final String distancia) {
        Datos.add(distancia);
        Distancia.setText("Distancia: " + distancia);
        if (Datos.size() > 50) { // Limpieza del arrayList
            for (int i = 0; i < 20; i++) {
                Datos.remove(i);
            }
        }
    }

    void alertaProximidad() throws InterruptedException { // Aviso de proximidad por sonido
        Lock lock = new ReentrantLock();
        if(Datos.size()>1) {
            int d1 = (int) Math.ceil(Float.parseFloat(Datos.get(Datos.size() - 1)) / 10);
            if (lock.tryLock()) {
                try {
                    switch (d1) {
                        case 1: {
                            sonar(20);
                            break;
                        }
                        case 2: {
                            sonar(300);
                            break;
                        }
                        case 3: {
                            sonar(400);
                            break;
                        }
                        case 4: {
                            sonar(600);
                            break;
                        }
                        case 5: case 6: {
                            sonar(800);
                            break;
                        }
                        case 7: case 8: {
                            sonar(1000);
                            break;
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    void sonar(long t) throws InterruptedException {
        if (listo) {
            soundPool.play(soundID, 0.5f, 0.5f, 1, 0, 1f);
            Thread.sleep(t);
        }
    }

    public void startVoiceRecognitionActivity() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"es");
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(!matches.isEmpty()){
                mList.setAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_1, matches));

                if (matches.contains("conectar")) {
                    if(btSocket.isConnected()) {
                        try {
                            findBT();
                            openBT();
                        } catch (IOException ex) {
                            Log.e(tag, "Error al conectar dispositivo bluetooth");
                        }
                    } else Toast.makeText(this, "Bluetooth ya conectado", Toast.LENGTH_SHORT).show();
                } else if (matches.contains("desconectar")) {
                    if(btSocket.isConnected()) {
                        try {
                            closeBT();
                        } catch (IOException ex) {
                            Log.e(tag, "Error al desconectar dispositivo bluetooth");
                        }
                    } else Toast.makeText(this, "Bluetooth no conectado", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
