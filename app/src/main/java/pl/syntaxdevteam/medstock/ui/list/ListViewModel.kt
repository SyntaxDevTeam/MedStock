package pl.syntaxdevteam.medstock.ui.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ListViewModel : ViewModel() {

    private val _itemNumbers = MutableLiveData<List<String>>().apply {
        value = listOf(
            "Ibuprofen 200 mg",
            "Paracetamol 500 mg",
            "Witamina D3 4000 IU"
        )
    }

    val itemNumbers: LiveData<List<String>> = _itemNumbers

    fun addMedication(rawName: String): Boolean {
        val normalizedName = rawName.trim()
        if (normalizedName.isBlank()) {
            return false
        }

        val current = _itemNumbers.value.orEmpty()
        _itemNumbers.value = current + normalizedName
        return true
    }
}
