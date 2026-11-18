package br.com.begood.bmp.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val pedirPermissoes = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.permissaoMudou()
    }

    private fun permissoesNecessarias(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        Build.VERSION.SDK_INT >= 30 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        // Android 8 a 10: gravar tags precisa da permissão de escrita
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!vm.temPermissao) pedirPermissoes.launch(permissoesNecessarias())
        setContent {
            // Confirmação do Android para alterar arquivos (editor de tags, Android 11+).
            val confirmarGravacao = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                vm.resultadoPermissaoGravacao(it.resultCode == Activity.RESULT_OK)
            }
            val pedido = vm.pedidoGravacao
            LaunchedEffect(pedido) {
                if (pedido != null) runCatching {
                    confirmarGravacao.launch(IntentSenderRequest.Builder(pedido.intentSender).build())
                }.onFailure { vm.resultadoPermissaoGravacao(false) }
            }
            AppCelular(vm, pedirPermissao = { pedirPermissoes.launch(permissoesNecessarias()) })
        }
    }

    override fun onResume() {
        super.onResume()
        // O usuário pode ter liberado a permissão nas configurações do Android.
        if (!vm.temPermissao) vm.permissaoMudou()
    }
}
