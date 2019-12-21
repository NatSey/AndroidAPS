package info.nightscout.androidaps.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import kotlinx.android.synthetic.main.dialog_fill.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.util.*
import kotlin.math.abs

class FillDialog : DialogFragmentWithDate() {

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("fill_insulinamount", fill_insulinamount.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_fill, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val maxInsulin = MainApp.getConstraintChecker().maxBolusAllowed.value()
        val bolusStep = ConfigBuilderPlugin.getPlugin().activePump!!.pumpDescription.bolusStep
        fill_insulinamount.setParams(savedInstanceState?.getDouble("fill_insulinamount")
            ?: 0.0, 0.0, maxInsulin, bolusStep, DecimalFormatter.pumpSupportedBolusFormat(), true, ok)
        val amount1 = SP.getDouble("fill_button1", 0.3)
        if (amount1 > 0) {
            fill_preset_button1.visibility = View.VISIBLE
            fill_preset_button1.text = DecimalFormatter.toPumpSupportedBolus(amount1) // + "U");
            fill_preset_button1.setOnClickListener { fill_insulinamount.value = amount1 }
        } else {
            fill_preset_button1.visibility = View.GONE
        }
        val amount2 = SP.getDouble("fill_button2", 0.0)
        if (amount2 > 0) {
            fill_preset_button2.visibility = View.VISIBLE
            fill_preset_button2.text = DecimalFormatter.toPumpSupportedBolus(amount2) // + "U");
            fill_preset_button2.setOnClickListener { fill_insulinamount.value = amount2 }
        } else {
            fill_preset_button2.visibility = View.GONE
        }
        val amount3 = SP.getDouble("fill_button3", 0.0)
        if (amount3 > 0) {
            fill_preset_button3.visibility = View.VISIBLE
            fill_preset_button3.text = DecimalFormatter.toPumpSupportedBolus(amount3) // + "U");
            fill_preset_button3.setOnClickListener { fill_insulinamount.value = amount3 }
        } else {
            fill_preset_button3.visibility = View.GONE
        }

    }

    override fun submit() {
        val insulin = SafeParse.stringToDouble(fill_insulinamount.text)
        val actions: LinkedList<String?> = LinkedList()

        val insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(Constraint(insulin)).value()
        if (insulinAfterConstraints > 0) {
            actions.add(MainApp.gs(R.string.fillwarning))
            actions.add("")
            actions.add(MainApp.gs(R.string.bolus) + ": " + "<font color='" + MainApp.gc(R.color.colorInsulinButton) + "'>" + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints) + MainApp.gs(R.string.insulin_unit_shortname) + "</font>")
            if (abs(insulinAfterConstraints - insulin) > 0.01)
                actions.add(MainApp.gs(R.string.bolusconstraintappliedwarning, MainApp.gc(R.color.warning), insulin, insulinAfterConstraints))
        }
        val siteChange = fill_catheter_change.isChecked
        if (siteChange)
            actions.add("" + "<font color='" + MainApp.gc(R.color.actionsConfirm) + "'>" + MainApp.gs(R.string.record_pump_site_change) + "</font>")
        val insulinChange = fill_cartridge_change.isChecked
        if (insulinChange)
            actions.add("" + "<font color='" + MainApp.gc(R.color.actionsConfirm) + "'>" + MainApp.gs(R.string.record_insulin_cartridge_change) + "</font>")
        val notes = notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
        if (eventTimeChanged)
            actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(eventTime))

        if (insulinAfterConstraints > 0 || fill_catheter_change.isChecked || fill_cartridge_change.isChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions))) {
                    if (insulinAfterConstraints > 0) {
                        val detailedBolusInfo = DetailedBolusInfo()
                        detailedBolusInfo.insulin = insulinAfterConstraints
                        detailedBolusInfo.context = context
                        detailedBolusInfo.source = Source.USER
                        detailedBolusInfo.isValid = false // do not count it in IOB (for pump history)
                        detailedBolusInfo.notes = notes
                        ConfigBuilderPlugin.getPlugin().commandQueue.bolus(detailedBolusInfo, object : Callback() {
                            override fun run() {
                                if (!result.success) {
                                    val i = Intent(MainApp.instance(), ErrorHelperActivity::class.java)
                                    i.putExtra("soundid", R.raw.boluserror)
                                    i.putExtra("status", result.comment)
                                    i.putExtra("title", MainApp.gs(R.string.treatmentdeliveryerror))
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    MainApp.instance().startActivity(i)
                                }
                            }
                        })
                    }
                    if (siteChange) NSUpload.uploadEvent(CareportalEvent.SITECHANGE, eventTime, notes)
                    if (insulinChange) NSUpload.uploadEvent(CareportalEvent.INSULINCHANGE, eventTime + 1000, notes)
                }
            }
        } else {
            OKDialog.show(activity, "", MainApp.gs(R.string.no_action_selected), null)
        }
        dismiss()
    }
}