package com.example.dacha.ui.products

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.core.util.forEach
import androidx.fragment.app.viewModels
import com.esafirm.imagepicker.features.ImagePickerConfig
import com.esafirm.imagepicker.features.ImagePickerMode
import com.esafirm.imagepicker.features.ReturnMode
import com.esafirm.imagepicker.features.registerImagePicker
import com.example.dacha.R
import com.example.dacha.data.model.*
import com.example.dacha.databinding.PurchaseBottomSheetBinding
import com.example.dacha.ui.home.HomeViewModel
import com.example.dacha.utils.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class PurchaseBottomFragment(
    private val purchase: PurchaseModel?,
    private val listOfPP: List<PlanProductModel>,
    val people: List<SimplePersonModel>,
    private val event: EventModel,
    val person: PersonModel
) : BottomSheetDialogFragment() {

    lateinit var binding: PurchaseBottomSheetBinding
    val viewModel: ProductsViewModel by viewModels()
    private val homeVM: HomeViewModel by viewModels()
    var closeFunction: ((Boolean) -> Unit)? = null
    var isSuccessAddTask: Boolean = false

    var resultProducts = mutableMapOf<String, ResultProductModel>()
    var planProducts = mutableMapOf<String, PlanProductModel>()
    var payer = purchase?.purchaseInfo?.paid
    var checkImage = purchase?.purchaseInfo?.photo

    private val launcher = registerImagePicker { images ->
        if (images.isNotEmpty()) uploadImages(images[0].uri)
    }

    private val pProdToDelete = mutableMapOf<String, PlanProductModel>()
    private val pProdToAdd = mutableMapOf<String, PlanProductModel>()

    private val bigSize = 8
    private val bigHeight = 1200
    private val bigWidth = 0

    var fullList = mutableListOf<String>()

    private lateinit var lvAdapter: ArrayAdapter<String>

    override fun getTheme() = R.style.AppBottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            PurchaseBottomSheetBinding.bind(
                inflater.inflate(
                    R.layout.purchase_bottom_sheet,
                    container
                )
            )

        listOfPP.forEach {
            planProducts[it.pProduct.toString()] = it
        }
        purchase?.resultProducts?.forEach {
            resultProducts[it.value.rProduct.toString()] = it.value
        }

        fullList.addAll(resultProducts.keys)
        fullList.addAll(planProducts.keys)

        val payerPicker = binding.personFilledExposed
        val tvMarket = binding.etPurchaseMarket
        if (purchase == null) {
            tvMarket.hint = getString(R.string.market)
            payerPicker.hint = getString(R.string.who_buy)
        } else {
            tvMarket.hint = purchase.purchaseInfo?.market
            payerPicker.hint = purchase.purchaseInfo?.paid?.name
        }
        val lvProducts = binding.resultProductsPicker

        val mapOfPeople = mutableMapOf<String, SimplePersonModel>()
        people.forEach {
            mapOfPeople[it.name.toString()] = it
        }

        val payerAdapter = ArrayAdapter(
            this.requireContext(),
            R.layout.drop_down_item,
            mapOfPeople.keys.toMutableList()
        )
        payerPicker.setAdapter(payerAdapter)
        payerPicker.setOnItemClickListener { _, _, i, _ ->
            payer = mapOfPeople[mapOfPeople.keys.toList()[i]]
        }
        lvAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_multiple_choice,
            fullList
        )
        lvProducts.adapter = lvAdapter

        if (fullList.size > bigSize) {
            val params = lvProducts.layoutParams
            params.height = bigHeight
            params.width = bigWidth
            lvProducts.layoutParams = params
        }

        fullList.forEach {
            if (it in resultProducts.keys.toList()) {
                lvProducts.setItemChecked(fullList.indexOf(it), true)
            } else lvProducts.setItemChecked(fullList.indexOf(it), false)
        }
        lvProducts.setOnItemClickListener { _, _, i, _ ->
            if (fullList[i] !in resultProducts.keys) {
                showAddResultDialog(lvProducts, fullList, i)
            } else {
                val price = resultProducts[fullList[i]]?.pPrice
                showChangeResultDialog(lvProducts, fullList, i, price)
            }
        }

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDeletePurchase.setOnClickListener {
            viewModel.deletePurchase(event.eInfo?.eKey.toString(), getPurchases())
        }

        binding.btnAddCheck.setOnClickListener {
            launcher.launch(ImagePickerConfig {
                mode = ImagePickerMode.SINGLE
                returnMode = ReturnMode.ALL
                arrowColor = R.color.white
                imageTitle = getString(R.string.choose_photo)
                theme = R.style.Theme_Dacha
                doneButtonText = getString(R.string.done)
            }
            )
        }

        binding.purchaseDoneBtn.setOnClickListener {
            if (purchase == null) viewModel.addPurchase(
                event.eInfo?.eKey.toString(),
                getPurchases()
            )
            else viewModel.updatePurchase(event.eInfo?.eKey.toString(), getPurchases())
        }

        binding.addResultDialogBtn.setOnClickListener {
            showAddNewProductDialog()
        }
        observer()
    }

    private fun observer() {
        viewModel.addPurchase.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.show()
                }
                is UiState.Failure -> {
                    binding.progressBar.hide()
                    toast(state.error)
                }
                is UiState.Success -> {
                    isSuccessAddTask = true
                    binding.progressBar.hide()
                    updatePlans(pProdToDelete.values.toList(), pProdToAdd.values.toList())
                    homeVM.addNews(
                        news(
                            person,
                            "${getString(R.string.add_purchase)} ${state.data.purchaseInfo?.market}"
                        )
                    )
                    toast(getString(R.string.purchase) + " " + getString(R.string.added))
                    this.dismiss()
                }
            }
        }
        viewModel.updatePurchase.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.show()
                }
                is UiState.Failure -> {
                    binding.progressBar.hide()
                    toast(state.error)
                }
                is UiState.Success -> {
                    isSuccessAddTask = true
                    binding.progressBar.hide()
                    updatePlans(pProdToDelete.values.toList(), pProdToAdd.values.toList())
                    homeVM.addNews(
                        news(
                            person,
                            "${getString(R.string.update_purchase)} ${state.data.purchaseInfo?.market}"
                        )
                    )
                    toast(getString(R.string.purchase) + " " + getString(R.string.updated))
                    this.dismiss()
                }
            }
        }
        viewModel.deletePurchase.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.show()
                }
                is UiState.Failure -> {
                    binding.progressBar.hide()
                    toast(state.error)
                }
                is UiState.Success -> {
                    isSuccessAddTask = true
                    binding.progressBar.hide()
                    resultProducts.values.forEach {
                        pProdToAdd[it.rProduct.toString()] = PlanProductModel(
                            pAmount = it.rAmount,
                            pKey = it.rKey,
                            pProduct = it.rProduct,
                            pWhose = it.rWhose
                        )
                    }
                    updatePlans(pProdToDelete.values.toList(), pProdToAdd.values.toList())
                    homeVM.addNews(
                        news(
                            person,
                            "${getString(R.string.delete_purchase)} ${state.data.purchaseInfo?.market}"
                        )
                    )
                    toast(getString(R.string.purchase) + " " + getString(R.string.deleted))
                    this.dismiss()
                }
            }
        }
    }

    private fun uploadImages(image: Uri) {
        viewModel.onUploadCheck(image) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.show()
                }
                is UiState.Failure -> {
                    binding.progressBar.hide()
                    toast(state.error)
                }
                is UiState.Success -> {
                    binding.progressBar.hide()
                    checkImage = state.data.second
                    homeVM.addNews(
                        news(
                            person,
                            getString(R.string.add_check, purchase?.purchaseInfo?.market)
                        )
                    )
                    toast(getString(R.string.photo) + " " + getString(R.string.added))
                }
            }
        }
    }

    private fun showAddNewProductDialog() {
        val dialog = requireContext().createDialog(R.layout.add_new_result_product_dialog, true)
        val nameEt = dialog.findViewById<EditText>(R.id.name_result_dialog_et)
        val amountEt = dialog.findViewById<EditText>(R.id.amount_result_dialog_et)
        val priceEt = dialog.findViewById<EditText>(R.id.price_result_dialog_et)
        val listView = dialog.findViewById<ListView>(R.id.new_result_picker)
        val allPeople = event.ePeople!!
        val adapter = ArrayAdapter(
            this.requireContext(),
            android.R.layout.simple_list_item_multiple_choice,
            (allPeople).map { it.name }
        )
        listView.adapter = adapter
        val button = dialog.findViewById<MaterialButton>(R.id.add_new_result_dialog_btn)
        val btnAll = dialog.findViewById<MaterialButton>(R.id.new_product_all_people_btn)
        val people = mutableListOf<SimplePersonModel>()
        btnAll.setOnClickListener {
            for (i in allPeople.indices)
                listView.setItemChecked(i, true)
        }
        button.setOnClickListener {
            if (nameEt.text.toString().isNotEmpty() && amountEt.text.toString()
                    .isNotEmpty() && priceEt.text.toString()
                    .isNotEmpty() && listView.checkedItemCount > 0
            ) {
                listView.checkedItemPositions.forEach { key, value ->
                    if (value) {
                        people.add(allPeople[key])
                    }
                }
                fullList.add(nameEt.text.toString())
                resultProducts[nameEt.text.toString()] = ResultProductModel(
                    amountEt.text.toString().toDouble(),
                    null,
                    priceEt.text.toString().toDouble(),
                    nameEt.text.toString(),
                    people
                )

                lvAdapter.notifyDataSetChanged()
                binding.resultProductsPicker.setItemChecked(
                    fullList.indexOf(nameEt.text.toString()),
                    true
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showAddResultDialog(lvProducts: ListView, fullList: List<String>, i: Int) {
        val dialog = requireContext().createDialog(R.layout.add_result_product_dialog, true)
        val button = dialog.findViewById<MaterialButton>(R.id.add_result_dialog_btn)
        val editText = dialog.findViewById<EditText>(R.id.add_result_dialog_et)
        editText.postDelayed({
            val keyboard =
                context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.showSoftInput(editText, 0)
        }, 200)
        val tv = dialog.findViewById<TextView>(R.id.tv_product_top)
        tv.text = fullList[i]
        button.setOnClickListener {
            if (editText.text.toString().isNotEmpty()) {
                lvProducts.setItemChecked(i, true)
                resultProducts[fullList[i]] = ResultProductModel(
                    planProducts[fullList[i]]?.pAmount,
                    planProducts[fullList[i]]?.pKey.toString(),
                    editText.text.toString().toDouble(),
                    planProducts[fullList[i]]?.pProduct,
                    planProducts[fullList[i]]?.pWhose
                )
                if (fullList[i] in pProdToAdd.keys) pProdToAdd.remove(fullList[i])
                pProdToDelete[fullList[i]] = planProducts[fullList[i]] as PlanProductModel
                planProducts.remove(fullList[i])
                dialog.dismiss()
            }
        }

        dialog.setOnCancelListener {
            lvProducts.setItemChecked(i, !lvProducts.isItemChecked(i))
        }
        dialog.show()
    }

    private fun showChangeResultDialog(
        lvProducts: ListView,
        fullList: List<String>,
        i: Int,
        price: Double?
    ) {
        val dialog = requireContext().createDialog(R.layout.change_result_product_dialog, true)
        val btnChange = dialog.findViewById<MaterialButton>(R.id.change_result_dialog_btn)
        val btnDelete = dialog.findViewById<MaterialButton>(R.id.delete_result_dialog_btn)
        val editText = dialog.findViewById<EditText>(R.id.change_result_dialog_et)
        val tv = dialog.findViewById<TextView>(R.id.tv_product_top)
        if (price != null) editText.hint = price.toString()
        tv.text = fullList[i]
        btnChange.setOnClickListener {
            if (editText.text.toString().isNotEmpty()) {
                lvProducts.setItemChecked(i, true)
                resultProducts[fullList[i]]?.pPrice = editText.text.toString().toDouble()
                dialog.dismiss()
            }
        }
        btnDelete.setOnClickListener {
            lvProducts.setItemChecked(i, false)

            planProducts[fullList[i]] = PlanProductModel(
                resultProducts[fullList[i]]?.rAmount,
                resultProducts[fullList[i]]?.rKey,
                resultProducts[fullList[i]]?.rProduct,
                resultProducts[fullList[i]]?.rWhose
            )
            if (planProducts[fullList[i]] as PlanProductModel !in listOfPP) {
                pProdToAdd[fullList[i]] =
                    planProducts[fullList[i]] as PlanProductModel
            }
            if (fullList[i] in pProdToDelete.keys) pProdToDelete.remove(fullList[i])

            resultProducts.remove(fullList[i])
            dialog.dismiss()
        }
        dialog.setOnCancelListener {
            lvProducts.setItemChecked(i, !lvProducts.isItemChecked(i))
        }
        dialog.show()
    }

    private fun updatePlans(
        toDelete: List<PlanProductModel>,
        toAdd: List<PlanProductModel>
    ) {
        toDelete.forEach {
            viewModel.deletePlanProduct(event.eInfo?.eKey.toString(), it)
        }
        toAdd.forEach {
            viewModel.addPlanProduct(event.eInfo?.eKey.toString(), it)
        }
    }

    private fun getPurchases(): PurchaseModel {
        val market =
            binding.etPurchaseMarket.text.toString().ifEmpty { purchase?.purchaseInfo?.market }
        val paid = payer
        val result = mutableMapOf<String, ResultProductModel>()
        resultProducts.forEach { (_, product) ->
            result[product.rKey.toString()] = product
        }
        return PurchaseModel(
            PurchaseInfo(purchase?.purchaseInfo?.key, market, checkImage, paid),
            result
        )
    }

    fun setDismissListener(function: ((Boolean) -> Unit)?) {
        closeFunction = function
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        closeFunction?.invoke(isSuccessAddTask)
    }
}