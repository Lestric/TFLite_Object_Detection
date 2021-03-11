package org.tensorflow.lite.examples.detection;

import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class AudioReturnActivity extends AppCompatActivity {
    private TextToSpeech mTTS;
    //private EditText mEditText;


    //Diese Methode wird beim drücken des Audioausgabe Button aufgerufen und ihr wird der Button zum aktivieren, sowie der Ausgabetext und die float Werte für pitch und speed der Sprache übergeben
    public void onButtonSpeak(Button mButtonSpeak, String text, float pitch, float speed){
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS){
                    int result = mTTS.setLanguage(Locale.GERMAN);
                    if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                        Log.e("TTS", "Language not supported");
                    }else{
                        mButtonSpeak.setEnabled(true);
                    }
                } else {
                    Log.e("TTS", "Initialization failed");
                }
            }
        });

        speak(text, pitch, speed);

    }

    private void speak(String text, float pitch, float speed){

        if(pitch < 0.1) pitch = 0.1f;
        if(speed < 0.1) speed = 0.1f;

        if(text == null) text = "Fehler";

        mTTS.setPitch(pitch);
        mTTS.setSpeechRate(speed);

        //durch Queue_Flush wird der Text wenn neuer Text zum sprechen kommt unterbrochen und der neue wird ausgegeben. alternative wäre Queue_ADD, dabei würde der text immer angehängt
        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);

    }

    @Override
    protected void onDestroy() {

        if(mTTS != null){
            mTTS.stop();
            mTTS.shutdown();
        }

        super.onDestroy();
    }
}
