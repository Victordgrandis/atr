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
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends Activity {
    /**
     * Variables correspondientes a las vistas
     */
    TextView Conectado, Distancia;
    Button Boton;
    public ListView mList;

    /**
     * Sonido
     */
    boolean listo= true; // Inicialización del sonido
    SoundPool soundPool;
    int soundID; // Almacenamiento de sonidos desde /raw

    /**
     * Variables Bluetooth
     */
    BluetoothAdapter btAdapter;
    BluetoothSocket btSocket;
    InputStream mmInputStream;

    /**
     * Hilos
     */
    Thread workerThread, alertaThread;
    volatile boolean stopWorker;

    /**
     * Almacenamiento de datos
     */
    ArrayList<String> Datos;
    byte[] readBuffer;
    int readBufferPosition;

    /**
     * Constantes
     */
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
        Boton = (Button) findViewById(R.id.boton);
        mList = (ListView) findViewById(R.id.list);
        Datos = new ArrayList<>();
        inicializarSonido();

        try {
            buscarBT();
            conectarBT();
        } catch (IOException ex) {
            Log.e(tag, "Error al conectar dispositivo bluetooth");
        }

        Boton.setOnClickListener(new View.OnClickListener() {
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
    }

    /**
     * Métodos de control del Bluetooth
     */

    private void buscarBT() {
        // Inicializa el adaptador de bluetooth
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!btAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }
    }

    private void conectarBT() throws IOException {
        // Conecta el socket de bluetooth
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        btSocket = device.createInsecureRfcommSocketToServiceRecord(uuid);
        btSocket.connect();
        mmInputStream = btSocket.getInputStream();
        Conectado.setText(R.string.conectado);
        Conectado.setTextColor(getResources().getColor(R.color.green));
        escucharDatos();
        alertar();
    }

    void cerrarBT() throws IOException {
        // Finaliza la conexión bluetooth
        stopWorker = true;
        mmInputStream.close();
        btSocket.close();
        Conectado.setText(R.string.desconectado);
        Conectado.setTextColor(getResources().getColor(R.color.red));
    }

    /**
     * Métodos de escucha del flujo de datos por bluetooth y su almacenamiento
     */

    private void escucharDatos() {
        // Recibe el flujo de datos por bluetooth
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false; // Continúe ejecutando
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

    @SuppressLint("SetTextI18n")
    void almacenarDatos(final String distancia) {
        // Guarda los datos que llegan por bluetooth en un arrayList
        Datos.add(distancia);
        Distancia.setText("Distancia: " + distancia);
        if (Datos.size() > 100) {
            // Limpieza del arrayList
            for (int i = 0; i < 50; i++) {
                Datos.remove(i);
            }
        }
    }

    /**
     * Métodos de manejo de las alertas de sonido
     */

    void inicializarSonido() {
        // Seteo inicial de parámetros de la librería SoundPool para alertas por sonido
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

    void alertar(){
        // Hilo que escucha ininterrumpidamente el último dato obtenido y alerta la proximidad
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

    void alertaProximidad() throws InterruptedException {
        // Avisa proximidad por sonido
        Lock lock = new ReentrantLock(); // Cerrojo de control de concurrencia
        if(Datos.size() > 1 && Datos.size() < 99) {
            int d = (int) Math.ceil(Float.parseFloat(Datos.get(Datos.size() - 1)) / 10); // Redondeo hacia arriba de la distancia
            if (lock.tryLock()) {
                try {
                    switch (d) {
                        case 1: { // Distancia < 10cm
                            sonar(20);
                            break;
                        }
                        case 2: { // 10cm < Distancia < 20cm
                            sonar(300);
                            break;
                        }
                        case 3: { // 20cm < Distancia < 30cm
                            sonar(400);
                            break;
                        }
                        case 4: { // 30cm < Distancia < 40cm
                            sonar(600);
                            break;
                        }
                        case 5: case 6: { // 40cm < Distancia < 60cm
                            sonar(800);
                            break;
                        }
                        case 7: case 8: { // 60cm < Distancia < 80cm
                            sonar(1000);
                            break;
                        }
                    }
                } finally {
                    lock.unlock(); // Libera el cerrojo
                }
            }
        }
    }

    void sonar(long t) throws InterruptedException {
        // Si el sonido está correctamente inicializado lo lanza y duerme el hilo
        if (listo) {
            soundPool.play(soundID, 0.5f, 0.5f, 1, 0, 1f);
            Thread.sleep(t);
        }
    }

    /**
     * Métodos de reconocimiento de voz
     */

    public void startVoiceRecognitionActivity() {
        // Llama a la aplicación de reconocimiento de voz del dispositivo
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es");
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Recibe respuesta de la aplicación de reconocimiento de voz del dispositivo
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            // Respuesta correcta
            ArrayList<String> resultados = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); // ArrayList donde almacenar las palabras interpretadas
            if(!resultados.isEmpty()){
                mList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, resultados)); // Muestra matches en ListView

                /**
                 * Acciones lanzadas por comando de voz
                 */
                if (resultados.contains("conectar")) {
                    //if(btSocket.isConnected()) {
                        try {
                            buscarBT();
                            conectarBT();
                        } catch (IOException ex) {
                            Log.e(tag, "Error al conectar dispositivo bluetooth");
                        }
                    //} else Toast.makeText(this, "Bluetooth ya conectado", Toast.LENGTH_SHORT).show();
                } else if (resultados.contains("desconectar")) {
                    if(btSocket.isConnected()) {
                        try {
                            cerrarBT();
                        } catch (IOException ex) {
                            Log.e(tag, "Error al desconectar dispositivo bluetooth");
                        }
                    } else Toast.makeText(this, "Bluetooth no conectado", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}