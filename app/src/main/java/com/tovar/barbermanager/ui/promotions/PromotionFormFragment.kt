package com.tovar.barbermanager.ui.promotions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.tovar.barbermanager.R
import com.tovar.barbermanager.data.Dates
import com.tovar.barbermanager.data.PromotionForm
import com.tovar.barbermanager.data.PromotionItem
import com.tovar.barbermanager.data.local.DiscountType
import com.tovar.barbermanager.data.model.ServiceCategory
import com.tovar.barbermanager.databinding.FragmentPromotionFormBinding
import com.tovar.barbermanager.ui.util.Format
import kotlinx.coroutines.launch

/** Alta y edición de promociones con cupón (administrador). */
class PromotionFormFragment : Fragment() {

    private var _binding: FragmentPromotionFormBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PromotionFormViewModel by lazy {
        ViewModelProvider(this)[PromotionFormViewModel::class.java]
    }

    private var promotionId = 0L
    private var couponId = 0L
    private var bound = false
    private var startIso: String? = null
    private var endIso: String? = null
    private var percentType = true
    private var categoryName: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentPromotionFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        promotionId = requireArguments().getLong("promotionId", 0L)
        applyInsets()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.formTitle.setText(
            if (promotionId == 0L) R.string.promo_form_new_title else R.string.promo_form_edit_title
        )
        setupDropdowns()
        setupDates()
        clearErrorsOnEdit()
        binding.saveButton.setOnClickListener { attemptSave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.formState.collect { state ->
                        if (state.loaded && !bound) {
                            bound = true
                            state.item?.let { bind(it) }
                        }
                    }
                }
                launch {
                    viewModel.saved.collect {
                        Snackbar.make(binding.root, R.string.promo_saved, Snackbar.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                }
                launch {
                    viewModel.codeTaken.collect {
                        binding.codeLayout.error = getString(R.string.promo_error_code_taken)
                        binding.saveButton.isEnabled = true
                    }
                }
            }
        }
        viewModel.load(promotionId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyInsets() {
        val headerBaseTop = binding.formHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.formRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.formHeader.updatePadding(top = headerBaseTop + bars.top)
            root.updatePadding(left = bars.left, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    private fun setupDropdowns() {
        val typeLabels = listOf(getString(R.string.promo_type_percent), getString(R.string.promo_type_amount))
        binding.typeInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, typeLabels))
        binding.typeInput.setText(typeLabels[0], false)
        binding.typeInput.setOnItemClickListener { _, _, position, _ -> percentType = position == 0 }

        val all = getString(R.string.promo_category_all)
        val categoryLabels = listOf(all) + ServiceCategory.entries.map { getString(it.titleRes) }
        binding.categoryInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categoryLabels)
        )
        binding.categoryInput.setText(all, false)
        binding.categoryInput.setOnItemClickListener { _, _, position, _ ->
            categoryName = if (position == 0) null else ServiceCategory.entries[position - 1].name
        }
    }

    private fun setupDates() {
        binding.startsInput.setOnClickListener { pickDate(startIso) { startIso = it; showDates() } }
        binding.endsInput.setOnClickListener { pickDate(endIso ?: startIso) { endIso = it; showDates() } }
    }

    private fun pickDate(current: String?, onPicked: (String) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.promo_hint_starts)
            .setSelection(Dates.utcMidnightMillis(current ?: Dates.today()))
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(Dates.isoFromUtcMillis(it)) }
        picker.show(childFragmentManager, "promo_date")
    }

    private fun showDates() {
        binding.startsInput.setText(startIso?.let { Dates.display(it) }.orEmpty())
        binding.endsInput.setText(endIso?.let { Dates.display(it) }.orEmpty())
        binding.startsLayout.error = null
        binding.endsLayout.error = null
    }

    private fun clearErrorsOnEdit() {
        binding.titleInput.doAfterTextChanged { binding.titleLayout.error = null }
        binding.descriptionInput.doAfterTextChanged { binding.descriptionLayout.error = null }
        binding.codeInput.doAfterTextChanged { binding.codeLayout.error = null }
        binding.valueInput.doAfterTextChanged { binding.valueLayout.error = null }
        binding.minPriceInput.doAfterTextChanged { binding.minPriceLayout.error = null }
        binding.maxUsesInput.doAfterTextChanged { binding.maxUsesLayout.error = null }
    }

    private fun bind(item: PromotionItem) {
        val promo = item.promotion
        val coupon = item.coupon
        binding.titleInput.setText(promo.title)
        binding.descriptionInput.setText(promo.description)
        binding.conditionsInput.setText(promo.conditions)
        startIso = isoOfMillis(promo.startsAt)
        endIso = isoOfMillis(promo.endsAt)
        showDates()
        binding.activeSwitch.isChecked = promo.active
        if (coupon != null) {
            couponId = coupon.id
            binding.codeInput.setText(coupon.code)
            percentType = coupon.discountType == DiscountType.PERCENT
            binding.typeInput.setText(
                getString(if (percentType) R.string.promo_type_percent else R.string.promo_type_amount),
                false
            )
            binding.valueInput.setText(
                if (percentType) coupon.discountValue.toString()
                else (coupon.discountValue / 100.0).toString()
            )
            categoryName = coupon.serviceCategory
            binding.categoryInput.setText(
                coupon.serviceCategory?.let { getString(ServiceCategory.from(it).titleRes) }
                    ?: getString(R.string.promo_category_all),
                false
            )
            if (coupon.minPriceCents > 0) {
                binding.minPriceInput.setText((coupon.minPriceCents / 100.0).toString())
            }
            binding.maxUsesInput.setText(coupon.maxUses?.toString().orEmpty())
        }
    }

    private fun isoOfMillis(millis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return Dates.isoOf(cal)
    }

    private fun attemptSave() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        val description = binding.descriptionInput.text?.toString()?.trim().orEmpty()
        val conditions = binding.conditionsInput.text?.toString()?.trim().orEmpty()
        val code = binding.codeInput.text?.toString()?.trim()?.uppercase().orEmpty()
        val valueText = binding.valueInput.text?.toString().orEmpty()
        val minText = binding.minPriceInput.text?.toString().orEmpty()
        val maxText = binding.maxUsesInput.text?.toString()?.trim().orEmpty()

        var firstInvalid: View? = null
        fun fail(layout: com.google.android.material.textfield.TextInputLayout, message: Int, focus: View) {
            layout.error = getString(message)
            if (firstInvalid == null) firstInvalid = focus
        }

        if (title.isEmpty()) fail(binding.titleLayout, R.string.promo_error_title, binding.titleInput)
        if (description.isEmpty()) {
            fail(binding.descriptionLayout, R.string.promo_error_description, binding.descriptionInput)
        }
        val start = startIso
        val end = endIso
        if (start == null || end == null) {
            if (start == null) fail(binding.startsLayout, R.string.promo_error_dates, binding.startsInput)
            if (end == null) fail(binding.endsLayout, R.string.promo_error_dates, binding.endsInput)
        } else if (end < start) {
            fail(binding.endsLayout, R.string.promo_error_dates_order, binding.endsInput)
        }

        var discountValue = 0L
        var minCents = 0L
        var maxUses: Int? = null
        if (code.isNotEmpty() || valueText.isNotEmpty()) {
            if (!Regex("^[A-Z0-9]{4,20}$").matches(code)) {
                fail(binding.codeLayout, R.string.promo_error_code, binding.codeInput)
            }
            if (percentType) {
                val pct = valueText.trim().toIntOrNull()
                if (pct == null || pct !in 1..100) {
                    fail(binding.valueLayout, R.string.promo_error_value_percent, binding.valueInput)
                } else discountValue = pct.toLong()
            } else {
                val cents = Format.parsePrice(valueText)
                if (cents == null) {
                    fail(binding.valueLayout, R.string.promo_error_value_amount, binding.valueInput)
                } else discountValue = cents
            }
            if (minText.isNotBlank()) {
                val cents = Format.parsePrice(minText)
                if (cents == null) {
                    fail(binding.minPriceLayout, R.string.promo_error_min_price, binding.minPriceInput)
                } else minCents = cents
            }
            if (maxText.isNotEmpty()) {
                val uses = maxText.toIntOrNull()
                if (uses == null || uses <= 0) {
                    fail(binding.maxUsesLayout, R.string.promo_error_max_uses, binding.maxUsesInput)
                } else maxUses = uses
            }
        }
        firstInvalid?.let { it.requestFocus(); return }

        binding.saveButton.isEnabled = false
        viewModel.save(
            PromotionForm(
                promotionId = promotionId,
                couponId = couponId,
                title = title,
                description = description,
                conditions = conditions,
                startsAt = Dates.millis(start!!, "00:00"),
                endsAt = Dates.millis(end!!, "23:59"),
                active = binding.activeSwitch.isChecked,
                code = code.ifEmpty { null },
                discountType = if (percentType) DiscountType.PERCENT else DiscountType.AMOUNT_CENTS,
                discountValue = discountValue,
                serviceCategory = categoryName,
                minPriceCents = minCents,
                maxUses = maxUses
            )
        )
    }
}
