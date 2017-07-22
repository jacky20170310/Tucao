package me.sweetll.tucao.business.login.viewmodel

import android.app.Activity
import android.content.Intent
import android.databinding.BindingAdapter
import android.databinding.ObservableField
import android.net.Uri
import android.support.design.widget.Snackbar
import android.view.View
import android.widget.ImageView
import com.trello.rxlifecycle2.kotlin.bind
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.login.LoginActivity
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.load
import me.sweetll.tucao.extension.sanitizeHtml
import me.sweetll.tucao.extension.toast
import org.jsoup.nodes.Document

class LoginViewModel(val activity: LoginActivity): BaseViewModel() {

    val email = ObservableField<String>()
    val password = ObservableField<String>()
    val code = ObservableField<String>()
    val codeBytes = ObservableField<ByteArray>()

    val container = ObservableField<Int>(View.VISIBLE)
    val progress = ObservableField<Int>(View.GONE)

    init {
        initSession()
    }

    companion object {
        @BindingAdapter("app:imageUrl")
        @JvmStatic
        fun loadImage(imageView: ImageView, bytes: ByteArray?) {
            bytes?.let {
                imageView.load(imageView.context, it)
            }
        }
    }

    fun initSession() {
        rawApiService.login_get()
                .subscribeOn(Schedulers.io())
                .retryWhen(ApiConfig.RetryWithDelay())
                .subscribe({
                    checkCode()
                }, {
                    error ->
                    error.printStackTrace()
                })
    }

    fun checkCode() {
        rawApiService.checkCode()
                .subscribeOn(Schedulers.io())
                .retryWhen(ApiConfig.RetryWithDelay())
                .subscribe({
                    body ->
                    this.codeBytes.set(body.bytes())
                }, {
                    error ->
                    error.printStackTrace()
                    error.message?.toast()
                })
    }

    fun dismiss(view: View) {
        activity.setResult(Activity.RESULT_CANCELED)
        activity.supportFinishAfterTransition()
    }

    fun onClickCode(view: View) {
        checkCode()
    }

    fun onClickSignUp(view: View) {
        val intent = Intent("android.intent.action.VIEW")
        intent.data = Uri.parse("http://www.tucao.tv/index.php?m=member&c=index&a=register&siteid=1")
        activity.startActivity(intent)
    }

    fun onClickSignIn(view: View) {
        activity.showLoading()
        rawApiService.login_post(email.get(), password.get(), code.get())
                .bindToLifecycle(activity)
                .sanitizeHtml {
                    parseLoginResult(this)
                }
                .flatMap {
                    (code, msg) ->
                    if (code == 0) {
                        rawApiService.personal()
                    } else {
                        Observable.error(Error(msg))
                    }
                }
                .observeOn(Schedulers.io())
                .sanitizeHtml {
                    parsePersonal(this)
                }
                .doAfterTerminate {
                    activity.showLogin()
                }
                .subscribe({
                    user.email = email.get()
                    activity.setResult(Activity.RESULT_OK)
                    activity.supportFinishAfterTransition()
                }, {
                    error ->
                    error.printStackTrace()
                    Snackbar.make(activity.binding.container, error.message!!, Snackbar.LENGTH_SHORT).show()
                })
    }

    fun parseLoginResult(doc: Document): Pair<Int, String>{
        val content = doc.body().text()
        if ("登录成功" in content) {
            return Pair(0, "")
        } else {
            return Pair(1, content)
        }
    }

    private fun parsePersonal(doc: Document): Any {
        val name_div = doc.select("a.name")[0]
        user.name = name_div.text()

        // 目前返回个人头像地址
        val index_div = doc.select("div.index")[0]
        val avatar_img = index_div.child(0).child(0)
        user.avatar =  avatar_img.attr("src")
        return Object()
    }


}
