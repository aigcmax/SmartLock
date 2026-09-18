package com.example.smartlock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartlock.SmartLockApp
import com.example.smartlock.di.AppViewModelFactory
import com.example.smartlock.ui.home.HomeScreen
import com.example.smartlock.ui.home.HomeViewModel
import com.example.smartlock.ui.manage.DeviceManageScreen
import com.example.smartlock.ui.manage.DeviceManageViewModel
import com.example.smartlock.ui.password.ChangePasswordScreen
import com.example.smartlock.ui.password.ChangePasswordViewModel
import com.example.smartlock.ui.password.SetPasswordScreen
import com.example.smartlock.ui.password.SetPasswordViewModel
import com.example.smartlock.ui.scan.ScanScreen
import com.example.smartlock.ui.scan.ScanViewModel
import com.example.smartlock.ui.splash.SplashScreen
import com.example.smartlock.ui.splash.SplashViewModel

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val container = remember {
        (context.applicationContext as SmartLockApp).container
    }
    val factory = remember { AppViewModelFactory(container) }
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        // 启动页：判断去扫描 / 设置密码 / 首页
        composable(Routes.SPLASH) {
            val vm: SplashViewModel = viewModel(factory = factory)
            SplashScreen(
                viewModel = vm,
                onResolved = { destination ->
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // 设备扫描与匹配
        composable(Routes.SCAN) {
            val vm: ScanViewModel = viewModel(factory = factory)
            val activity = context.findActivity()
            ScanScreen(
                viewModel = vm,
                onBack = {
                    // 有上层就退栈，没有就退出 App
                    val popped = navController.popBackStack()
                    if (!popped) activity?.finish()
                },
                onPaired = {
                    navController.navigate(Routes.SET_PASSWORD) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                }
            )
        }

        // 首次设置密码
        composable(Routes.SET_PASSWORD) {
            val vm: SetPasswordViewModel = viewModel(factory = factory)
            SetPasswordScreen(
                viewModel = vm,
                onSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SET_PASSWORD) { inclusive = true }
                    }
                }
            )
        }

        // 首页：解锁 / 上锁
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = vm,
                onNavigateToManage = {
                    navController.navigate(Routes.DEVICE_MANAGE)
                }
            )
        }

        // 设备管理
        composable(Routes.DEVICE_MANAGE) {
            val vm: DeviceManageViewModel = viewModel(factory = factory)
            DeviceManageScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onChangePassword = {
                    navController.navigate(Routes.CHANGE_PASSWORD)
                },
                onRepair = {
                    navController.navigate(Routes.SCAN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onUnpaired = {
                    navController.navigate(Routes.SCAN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // 修改密码
        composable(Routes.CHANGE_PASSWORD) {
            val vm: ChangePasswordViewModel = viewModel(factory = factory)
            ChangePasswordScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}