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
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends Activity {
    /**
     * Variables de vistas
     */
    TextView Conectado, Distancia;
    Button botonConectar, botonSilenciar, botonDesconectar, botonActivar;
    public ListView mList;

    /**
     * Sonido
     */
    boolean listo= true; // Inicialización del sonido
    boolean silenciar = false;
    SoundPool soundPool;
    int soundID; // Almacenamiento de sonidos desde /raw
    private TextToSpeech mTts;

    /**
     * Variables Bluetooth
     */
    BluetoothAdapter btAdapter;
    BluetoothSocket btSocket;
    InputStream mmInputStream;

    /**
     * Hilos
     */
    Thread btThread, alertaThread;
    volatile boolean stopWorker;
    boolean cerca = false;

    /**
     * Almacenamiento de datos
     */
    ArrayList<String> Datos;
    byte[] readBuffer;
    int readBufferPosition;
    Float anteriorIzq = 0f, anteriorDer = 0f;
    /**
     * Constantes
     */
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String address = "98:D3:37:00:82:B3";
    private static final String tag = "alertaProximidad";
    public static final int RECONOCIMIENTO_VOZ = 1234;
    public static final int TTS = 1111;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Conectado = (TextView) findViewById(R.id.conectado);
        //Distancia = (TextView) findViewById(R.id.distancia);
        botonConectar = (Button) findViewById(R.id.botonConectar);
        botonDesconectar = (Button) findViewById(R.id.botonDesconectar);
        botonSilenciar = (Button) findViewById(R.id.botonSilencio);
        botonActivar = (Button) findViewById(R.id.botonActivar);
        Datos = new ArrayList<>();
        inicializarSonido();
/*
        try {
            buscarBT();
            conectarBT();
        } catch (IOException ex) {
            Log.e(tag, "Error al conectar dispositivo bluetooth");
        }
*/

        botonConectar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //reconocerVoz();
                try {
                    buscarBT();
                    conectarBT();
                } catch (IOException ex) {
                    Log.e(tag, "Error al conectar dispositivo bluetooth");
                }

            }
        });

        botonDesconectar.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    cerrarBT();
                }
                catch (IOException ex) { }
            }
        });

        botonSilenciar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                silenciar = true;

            }
        });

        botonActivar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                silenciar = false;

            }
        });
    }
    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch(keycode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MENU:
                reconocerVoz();
                return true;
        }

        return super.onKeyDown(keycode, e);
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
        try {
            avisoVoz("Conectado");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        try {
            avisoVoz("Desconectado");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        btThread = new Thread(new Runnable() {
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

        btThread.start();
    }

    @SuppressLint("SetTextI18n")
    void almacenarDatos(final String distancia) {
        // Guarda los datos que llegan por bluetooth en un arrayList
        Datos.add(distancia);
        if (Datos.size() > 1000) {
            // Limpieza del arrayList
            Datos.remove(1);
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
        Intent intent = new Intent();
        intent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(intent, TTS);
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
        // Avisa proximidad por sonido o TTS
        int sensor;
        Float distancia;
        ReentrantLock lock = new ReentrantLock(); // Cerrojo de control de concurrencia
        if (Datos.size() > 1) {
            if(Float.parseFloat(Datos.get(Datos.size() - 1)) > 2000){
                // Sensor izquierdo
                distancia = Float.parseFloat(Datos.get(Datos.size() - 1)) - 2000f;
                Log.d(tag,"Izquierda: " +distancia);
                sensor = 2;
            } else {
                // Sensor derecho
                distancia = Float.parseFloat(Datos.get(Datos.size() - 1)) - 1000f;
                Log.d(tag,"Derecha: " +distancia);
                sensor = 1;
            }

            /* Siempre devuelve falso
            if(lock.isHeldByCurrentThread()){
                Log.d(tag, "Lock held by current thread");
                if(Math.ceil(distancia/20) != Math.ceil(anteriorIzq/20) || Math.ceil(distancia/20) != Math.ceil(anteriorDer/20)){
                    lock.unlock();
                    Log.d(tag, "Desbloqueado por: "+distancia);
                }
            }*/

            if (lock.tryLock()) {
                try {
                    if (distancia < 100) {
                        // A corta distancia avisa por sonido
                        int d = (int) Math.ceil(distancia / 10); // Redondeo hacia arriba de la distancia
                        switch (d) {
                            case 1: { // Distancia < 10cm
                                sonar(20,sensor);
                                break;
                            }
                            case 2: { // 10cm < Distancia < 20cm
                                sonar(300,sensor);
                                break;
                            }
                            case 3: { // 20cm < Distancia < 30cm
                                sonar(400,sensor);
                                break;
                            }
                            case 4: { // 30cm < Distancia < 40cm
                                sonar(600,sensor);
                                break;
                            }
                            case 5:
                            case 6: { // 40cm < Distancia < 60cm
                                sonar(800,sensor);
                                break;
                            }
                            case 7:
                            case 8: { // 60cm < Distancia < 80cm
                                sonar(1000,sensor);
                                break;
                            }
                            case 9:
                            case 10: { // 80cm < Distancia < 100cm
                                sonar(2000,sensor);
                                break;
                            }
                        }
                    } else {
                        // A mayor distancia avisa por TTS
                        if(sensor == 1){
                            if(Math.ceil(distancia/10) == Math.ceil(anteriorDer/10)){
                                avisoVoz(String.valueOf(Math.round(distancia)) + " derecha");
                            }
                            anteriorDer = distancia;
                        } else {
                            if(Math.ceil(distancia/10) == Math.ceil(anteriorIzq/10)){
                                avisoVoz(String.valueOf(Math.round(distancia)) + " izquierda");
                            }
                            anteriorIzq = distancia;
                        }
                    }
                } finally {
                    lock.unlock(); // Libera el cerrojo
                }
            }
        }
    }

    void sonar(long t, int sensor) throws InterruptedException {
        // Si el sonido está correctamente inicializado lo lanza y duerme el hilo
        if(!silenciar) {
            if (listo) {
                if(sensor == 1){
                    soundPool.play(soundID, 0.0f, 0.5f, 1, 0, 1f);
                } else {
                    soundPool.play(soundID, 0.5f, 0.0f, 1, 0, 1f);
                }
                Thread.sleep(t);
            }
        }
    }

    void avisoVoz(String texto) throws InterruptedException {
        // Informa por voz la distancia del objeto
        if (!silenciar) {
            mTts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null);
            while (mTts.isSpeaking()) {
                // Espera a que termine de hablar
            }
        }
    }

    /**
     * Métodos de reconocimiento de voz y TTS
     */

    public void reconocerVoz() {
        // Llama a la aplicación de reconocimiento de voz del dispositivo
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es");
        startActivityForResult(intent, RECONOCIMIENTO_VOZ);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Recibe respuesta de la aplicación de reconocimiento de voz del dispositivo
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RECONOCIMIENTO_VOZ && resultCode == RESULT_OK) {
            /**
             * Respuesta Intent reconocimiento de voz
             */
            ArrayList<String> resultados = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); // ArrayList donde almacenar las palabras interpretadas
            if(!resultados.isEmpty()){

                /**
                 * Acciones lanzadas por comando de voz
                 */
                if (resultados.contains("conectar")) {
                    try {
                        buscarBT();
                        conectarBT();
                    } catch (IOException ex) {
                        Log.e(tag, "Error al conectar dispositivo bluetooth");
                    }
                } else if (resultados.contains("desconectar")) {
                    try {
                        cerrarBT();
                    } catch (IOException ex) {
                        Log.e(tag, "Error al desconectar dispositivo bluetooth");
                    }
                } else if (resultados.contains("activar")){
                    silenciar = false;
                    try {
                        avisoVoz("Activado");
                    } catch (InterruptedException e) {
                        Log.e(tag, "Error al activar sonido");
                    }
                } else if (resultados.contains("silenciar")){
                    silenciar = true;
                    try {
                        avisoVoz("Silenciado");
                    } catch (InterruptedException e) {
                        Log.e(tag, "Error al silenciar sonido");
                    }
                }
            }
        } else if (requestCode == TTS) {
            /**
             * Respuesta Intent inicialización TTS
             */
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // Inicializa el TTS
                mTts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        mTts.setLanguage(Locale.getDefault());
                    }
                });
            } else {
                // Instala datos faltantes del TTS
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }
}