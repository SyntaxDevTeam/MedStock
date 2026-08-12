package pl.syntaxdevteam.medstock.ui.medicationarchive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.download.UserMedicationRepository
import pl.syntaxdevteam.medstock.databinding.FragmentMedicationArchiveBinding

class MedicationArchiveFragment : Fragment() {
    private var _binding: FragmentMedicationArchiveBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMedicationArchiveBinding.inflate(inflater, container, false)
        val adapter = ArchivedMedicationAdapter()
        binding.recyclerviewMedicationArchive.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewMedicationArchive.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            val medications = withContext(Dispatchers.IO) { UserMedicationRepository(requireContext()).getAllArchived() }
            binding.textMedicationArchiveSummary.text = getString(R.string.medication_archive_summary, medications.size)
            binding.textMedicationArchiveEmpty.visibility = if (medications.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(medications)
        }
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private class ArchivedMedicationAdapter : RecyclerView.Adapter<ArchivedMedicationViewHolder>() {
    private val items = mutableListOf<ArchivedMedication>()
    fun submitList(data: List<ArchivedMedication>) { items.clear(); items.addAll(data); notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ArchivedMedicationViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_archived_medication, parent, false)
    )
    override fun onBindViewHolder(holder: ArchivedMedicationViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}

private class ArchivedMedicationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val name: TextView = view.findViewById(R.id.text_archived_medication_name)
    private val note: TextView = view.findViewById(R.id.text_archived_medication_note)
    fun bind(item: ArchivedMedication) {
        val strengthSuffix = item.strength.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
        name.text = itemView.context.getString(R.string.medication_list_title_with_strength, item.name, strengthSuffix)
        note.text = itemView.context.getString(
            R.string.medication_archive_item_details,
            item.archivedAtUtc,
            item.currentStock,
            item.unit.ifBlank { itemView.context.getString(R.string.medication_default_unit) },
            item.dosage.ifBlank { itemView.context.getString(R.string.medication_archive_no_dosage) }
        )
    }
}
