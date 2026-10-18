package com.tianma.xsmscode.ui.rule.edit;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.smscodf.zhuxf.R;
import com.google.android.material.textfield.TextInputEditText;
import com.tianma.xsmscode.common.constant.Const;
import com.tianma.xsmscode.common.utils.Utils;
import com.tianma.xsmscode.data.db.entity.SmsCodeRule;

import javax.inject.Inject;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatSpinner;
import butterknife.BindView;
import butterknife.ButterKnife;
import dagger.android.support.DaggerFragment;

/**
 * Rule edit fragment
 */
public class RuleEditFragment extends DaggerFragment implements RuleEditContract.View {

    public static final int EDIT_TYPE_CREATE = 1;
    public static final int EDIT_TYPE_UPDATE = 2;

    @IntDef({EDIT_TYPE_CREATE, EDIT_TYPE_UPDATE})
    public @interface RuleEditType {
    }

    static final String KEY_RULE_EDIT_TYPE = "rule_edit_type";
    static final String KEY_CODE_RULE = "code_rule";

    @BindView(R.id.rule_company_edit_text)
    TextInputEditText mCompanyEditText;

    @BindView(R.id.rule_keyword_edit_text)
    TextInputEditText mKeywordEditText;

    @BindView(R.id.rule_code_regex_quick_choose)
    Button mQuickChooseBtn;

    @BindView(R.id.rule_code_regex_edit_text)
    TextInputEditText mCodeRegexEditText;

    private Activity mActivity;

    private int mCodeTypeIndex = 0;

    @Inject
    RuleEditContract.Presenter mPresenter;

    public static RuleEditFragment newInstance(int ruleEditType, SmsCodeRule codeRule) {
        Bundle args = new Bundle();
        args.putInt(KEY_RULE_EDIT_TYPE, ruleEditType);
        args.putParcelable(KEY_CODE_RULE, codeRule);
        RuleEditFragment fragment = new RuleEditFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_rule_edit, container, false);
        ButterKnife.bind(this, rootView);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = getActivity();
        mQuickChooseBtn.setOnClickListener(v -> showQuickChooseDialog());

        mCodeRegexEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mPresenter.saveIfValid(getCurrentCodeRule());
                return true;
            }
            return false;
        });

        mPresenter.handleArguments(getArguments());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onDetach();
    }

    private void showQuickChooseDialog() {
        final String[] codeTypes = mActivity.getResources().getStringArray(R.array.sms_code_type_list);
        final TextInputEditText[] codeLenRef = new TextInputEditText[1];

        // 2026-10-18：改用自绘弹窗；自定义视图在填充回调里绑监听
        // 用数组持有 Builder：lambda 内引用时它可能尚未完成赋值
        final com.tianma.xsmscode.ui.dialog.CommonDialog.Builder[] builderRef =
                new com.tianma.xsmscode.ui.dialog.CommonDialog.Builder[1];
        com.tianma.xsmscode.ui.dialog.CommonDialog.Builder builder =
                builderRef[0] = new com.tianma.xsmscode.ui.dialog.CommonDialog.Builder(
                        getChildFragmentManager(), requireContext())
                .title(R.string.quick_choose)
                .customView(R.layout.dialog_code_regex_quick_chcoose, container -> {
                    AppCompatSpinner spinner = container.findViewById(R.id.rule_code_type_spinner);
                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                            mCodeTypeIndex = position;
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });
                    codeLenRef[0] = container.findViewById(R.id.code_rule_length_edit_text);
                })
                .negative(R.string.cancel)
                .positive(R.string.confirm, input -> {
                    TextInputEditText codeLenEditText = codeLenRef[0];
                    if (codeLenEditText == null) {
                        return;
                    }
                    String codeType = codeTypes[mCodeTypeIndex];
                    String codeLenText = codeLenEditText.getText().toString();
                    if (TextUtils.isEmpty(codeLenText)) {
                        codeLenEditText.setError(getString(R.string.code_length_empty_prompt));
                        return;
                    }
                    // (?<![0-9])[0-9]{4}(?![0-9])
                    String format = "(?<!%s)%s{%s}(?!%s)";
                    String codeRegex = String.format(format, codeType, codeType, codeLenText, codeType);
                    setText(mCodeRegexEditText, codeRegex);
                    // 校验通过后手动关闭（autoDismiss=false 不会自动关）
                    if (builderRef[0] != null) {
                        builderRef[0].dismiss();
                    }
                })
                .autoDismiss(false);
        builder.show();
    }

    private void setText(EditText editText, CharSequence text) {
        if (!TextUtils.isEmpty(text)) {
            editText.setText(text);
            editText.setSelection(text.length());
        }
    }

    private void setError(EditText editText, @StringRes int textId) {
        setError(editText, getString(textId));
    }

    private void setError(EditText editText, String error) {
        editText.setError(error);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_edit_rule, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_rules_tick:
                mPresenter.saveIfValid(getCurrentCodeRule());
                break;
            case R.id.action_save_as_template:
                mPresenter.saveAsTemplate(getCurrentCodeRule());
                break;
            case R.id.action_rule_help:
                showCodeRuleHelp();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private SmsCodeRule getCurrentCodeRule() {
        String company = mCompanyEditText.getText().toString();
        String keyword = mKeywordEditText.getText().toString();
        String codeRegex = mCodeRegexEditText.getText().toString();
        return new SmsCodeRule(company, keyword, codeRegex);
    }

    private void showCodeRuleHelp() {
        String ruleHelpUrl = Utils.getProjectDocUrl(Const.PROJECT_DOC_BASE_URL, Const.DOC_SMS_CODE_RULE_HELP);
        Utils.showWebPage(mActivity, ruleHelpUrl);
    }

    @Override
    public void displayCodeRule(SmsCodeRule codeRule) {
        if (codeRule != null) {
            setText(mCompanyEditText, codeRule.getCompany());
            setText(mKeywordEditText, codeRule.getCodeKeyword());
            setText(mCodeRegexEditText, codeRule.getCodeRegex());
        }
    }

    @Override
    public void clearAllErrorInfo() {
        setError(mCompanyEditText, null);
        setError(mKeywordEditText, null);
        setError(mCodeRegexEditText, null);
    }

    @Override
    public void onTemplateSaved(boolean success) {
        int msg = success ? R.string.save_template_succeed : R.string.save_template_failed;
        Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showErrorInfo(boolean companyValid, boolean keywordValid, boolean codeRegexValid) {
        if (!companyValid) {
            setError(mCompanyEditText, R.string.rule_company_empty_hint);
        }
        if (!keywordValid) {
            setError(mKeywordEditText, R.string.rule_keyword_empty_hint);
        }
        if (!codeRegexValid) {
            setError(mCodeRegexEditText, R.string.rule_code_regex_empty_hint);
        }
    }

    @Override
    public void hideSoftInput() {
        InputMethodManager imeManager = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imeManager != null && imeManager.isActive()) {
            imeManager.hideSoftInputFromWindow(mCodeRegexEditText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    @Override
    public void onCodeRuleSaved(boolean success) {
        if (success) {
            mActivity.onBackPressed();
        } else {
            Toast.makeText(mActivity, R.string.rule_duplicated_prompt, Toast.LENGTH_LONG).show();
        }
    }

}
