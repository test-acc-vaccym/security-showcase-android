package cz.koto.securityshowcase.ui.login

//import cz.koto.securityshowcase.ContextProvider
import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.content.Intent
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.net.Uri
import android.support.v4.app.ActivityCompat
import com.apollographql.android.rx2.Rx2Apollo
import cz.koto.securityshowcase.Login
import cz.koto.securityshowcase.SecurityConfig
import cz.koto.securityshowcase.api.SecurityShowcaseApiProvider
import cz.koto.securityshowcase.model.AuthRequestSimple
import cz.koto.securityshowcase.storage.CredentialStorage
import cz.koto.securityshowcase.ui.StateListener
import cz.koto.securityshowcase.utility.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.alfonz.view.StatefulLayout
import retrofit2.HttpException

class LoginViewModel(val context: Application) : /*BaseViewModel<ActivityLoginBinding>()*/AndroidViewModel(context), StateListener {

	val devAvailable = ObservableBoolean(SecurityConfig.isEndpointDev() && !SecurityConfig.isPackageRelease())

	val email: ObservableField<String> = ObservableField()
	val password: ObservableField<String> = ObservableField()

	val showSignIn = ObservableBoolean(false)

	val userNameChanged = object : Observable.OnPropertyChangedCallback() {
		override fun onPropertyChanged(p0: Observable?, p1: Int) {
			showSignIn.set(email.get().isNotEmpty())
		}
	}

	var stringChallenge = ""

	var loginAt by longPref("last_login_at")

	val state = ObservableField(StatefulLayout.CONTENT)


	init {
		CredentialStorage.forceLockScreenFlag()
		email.addOnPropertyChangedCallback(userNameChanged)
	}

	override fun onCleared() {
		super.onCleared()
		email.removeOnPropertyChangedCallback(userNameChanged)
	}


	override fun setProgress() {
		state.set(StatefulLayout.PROGRESS)
	}


	override fun setContent() {
		state.set(StatefulLayout.CONTENT)
	}


	fun signInRest() {
		setProgress()
		SecurityShowcaseApiProvider.restAuthRouter.loginJWT(AuthRequestSimple(
				email.get() ?: "",
				password.get() ?: ""))
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(
						{
							onSuccessfulLogin(it?.idToken)
						},
						{ error ->
							when (error) {
								is HttpException -> {
									when (error.code()) {
										401 -> {
											Logcat.e("Unauthorized, invalid credentials!")
											//TODO inform user
										}
										403 -> {
											Logcat.e("Forbidden, user is not allowed to access app!")
											//TODO inform user
										}
										else -> {
											Logcat.e("Unexpected HttpException during login!", error)
											//TODO inform user
										}
									}
								}
								else -> {
									Logcat.e("Unexpected login issue!", error)
									//TODO inform user
								}
							}
							setContent()
						})
	}

	fun signInGql() {
		setProgress()
		val query = Login.builder().email(email.get() ?: "").password(password.get() ?: "").build()
		Rx2Apollo.from(SecurityShowcaseApiProvider.gqlRouter.newCall(query))
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(
						{ onSuccessfulLogin(it.login()?.token()) },
						{ setContent(); it.printStackTrace() }
				)

	}

	private fun onSuccessfulLogin(token: String?) =
			if (isValidJWT(token)) {
				CredentialStorage
						.storeUser(token!!, email.get() ?: "", password.get() ?: "")
				loginAt = System.nanoTime()
				showMain()
			} else {
				setContent()
			}


	fun fillTest() {
		email.set(SecurityConfig.getTestEmail())
		password.set(SecurityConfig.getTestPass())
	}


	private fun showMain() {
		stringChallenge = ""
		//TODO use SingleLiveEvent from arch components instead.
		applicationEvents.onNext(ApplicationEvent.RequestMain)
	}

	fun followGithub() {
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kotomisak/security-showcase-android"))
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		ActivityCompat.startActivity(context, intent, null)
	}
}