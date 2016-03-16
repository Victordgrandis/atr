package com.atr.arduino.lentes;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
    // App Variables
    TextView Conectado,Cantidad1,Cantidad2,Cantidad3,Promedio,Error;
    Button conectarButton,desconectarButton;
    ArrayList<String> Datos;
    Float promedio,error;
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


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Conectado = (TextView) findViewById(R.id.conectado);
        Cantidad1 = (TextView) findViewById(R.id.cantidad1);
        Cantidad2 = (TextView) findViewById(R.id.cantidad2);
        Cantidad3 = (TextView) findViewById(R.id.cantidad3);
        Promedio = (TextView) findViewById(R.id.promedio);
        Error = (TextView) findViewById(R.id.error);
        conectarButton = (Button)findViewById(R.id.conectar);
        desconectarButton = (Button) findViewById(R.id.desconectar);
        Datos = new ArrayList<String>();

        conectarButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try
                {
                    findBT();
                    openBT();
                } catch (IOException ex) {
                }
            }
        });

        desconectarButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    closeBT();
                }
                catch (IOException ex) { }
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
        Conectado.setText("Conectado");
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

    void sendData() throws IOException {
    }

    void closeBT() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        btSocket.close();
        Conectado.setText("Desconectado");
        Conectado.setTextColor(getResources().getColor(R.color.red));
    }
    void mostrarDatos(String data){
        Datos.add(data);
        Cantidad1.setText("Distancia: "+data);
        if(Datos.size()>3) {
            promedio = (Float.parseFloat(Datos.get(Datos.size()-2)) + Float.parseFloat(Datos.get(Datos.size() - 3)) + Float.parseFloat(data))/3;
            error = Float.parseFloat(data) - promedio;
            Promedio.setText("Promedio: "+String.valueOf(promedio));
            Error.setText("Error: "+String.valueOf(error));
            Cantidad2.setText("Distancia: "+Datos.get(Datos.size() - 2));
            Cantidad3.setText("Distancia: "+Datos.get(Datos.size() - 3));
        }
        if(Datos.size()>50){
            for(int i = 0; i<20;i++){
                Datos.remove(i);
            }
        }
    }
}