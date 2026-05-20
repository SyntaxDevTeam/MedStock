package pl.syntaxdevteam.medstock.ui.reflow

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ReflowViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Katalog leków"
    }
    val text: LiveData<String> = _text
}
