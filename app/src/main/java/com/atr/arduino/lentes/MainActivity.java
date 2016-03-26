package com.atr.arduino.lentes;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends Activity {
    // App Variables
    TextView Conectado, Distancia, Promedio, Error;
    Button conectarButton, desconectarButton;
    ArrayList<String> Datos;
    Float promedio, error;
    boolean listo = true;
    // Sonido
    SoundPool soundPool;
    int soundID;
    // Bluetooth variables
    BluetoothAdapter btAdapter;
    BluetoothSocket btSocket;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String address = "30:14:06:26:01:02";
    private static final String tag = "MainActivity";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Conectado = (TextView) findViewById(R.id.conectado);
        Distancia = (TextView) findViewById(R.id.distancia);
        Promedio = (TextView) findViewById(R.id.promedio);
        Error = (TextView) findViewById(R.id.error);
        conectarButton = (Button) findViewById(R.id.conectar);
        desconectarButton = (Button) findViewById(R.id.desconectar);
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
                try
                {
                    findBT();
                    openBT();
                } catch (IOException ex) {
                    Log.e(tag, "Error al conectar dispositivo bluetooth");
                }
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
        beginListenForData();
    }

    private void beginListenForData() {
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
                                            mostrarDatos(data);
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
    void mostrarDatos(final String distancia) {
        Thread alertaThread;
        Datos.add(distancia);
        Distancia.setText("Distancia: " + distancia);
        alertaThread = new Thread (new Runnable(){
            public void run(){
                try {
                    alertaProximidad(Float.parseFloat(distancia));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        alertaThread.start();
        /* Cálculo del promedio de 3 muestras
        if (Datos.size() > 3) {
            promedio = (Float.parseFloat(Datos.get(Datos.size() - 2)) + Float.parseFloat(Datos.get(Datos.size() - 3)) + Float.parseFloat(distancia)) / 3;
            alertaProximidad(promedio);
            error = Float.parseFloat(distancia) - promedio;
            Promedio.setText("Promedio: " + String.valueOf(promedio));
            Error.setText("Error: " + String.valueOf(error));
        }
        */
        if (Datos.size() > 50) { // Limpieza del arrayList
            for (int i = 0; i < 20; i++) {
                Datos.remove(i);
            }
        }
    }

    void alertaProximidad(Float distancia) throws InterruptedException { // Aviso de proximidad por sonido
        int d = (int) Math.ceil(distancia / 10);
        switch (d) {
            case 1: {
                sonar(100);
                /*
                Promedio.setTextColor(Color.RED);
                if (listo) {
                    try {
                        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext()).setSound(soundUri);
                        notificationManager.notify(0, mBuilder.build());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                */
                break;
            }
            case 2: {
                sonar(300);
                break;
            }
            case 3:{
                sonar(400);
                break;
            }
            case 4: {
                break;
            }
        }
    }

    synchronized void sonar(long t) throws InterruptedException {
        if (listo) {
            soundPool.play(soundID, 0.5f, 0.5f, 1, 0, 1f);
            wait(t);
        }
    }
}