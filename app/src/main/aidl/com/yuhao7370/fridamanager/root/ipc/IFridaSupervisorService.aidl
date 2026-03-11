package com.yuhao7370.fridamanager.root.ipc;

import android.os.Bundle;

interface IFridaSupervisorService {
    Bundle start(String version, String binaryPath, String host, int port);
    Bundle stop();
    Bundle restart(String version, String binaryPath, String host, int port);
    Bundle getStatus();
}
