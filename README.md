# Control de gastos

Aplicación Android nativa en español para llevar varios presupuestos mensuales independientes en euros.

## Incluido

- Cuentas con límite y aviso de proximidad propios.
- Alta, edición y borrado de gastos del mes en curso.
- Saldo disponible destacado y aviso al acercarse o superar el límite.
- Archivado automático al cambiar de mes: conserva límite, gasto total y diferencia, y elimina los movimientos individuales cerrados.
- Historial separado por cuenta y confirmación antes de eliminar una cuenta.
- Teclado numérico para importes, calendario para elegir la fecha y orden manual de cuentas manteniendo pulsada una cuenta y arrastrándola.
- La última cuenta abierta se restaura al volver a iniciar la app, y el histórico incluye totales anuales de gasto y ahorro.

## Abrir y ejecutar

1. Abra esta carpeta en Android Studio (Hedgehog o posterior).
2. En **Settings > Build, Execution, Deployment > Build Tools > Gradle**, elija **Use Gradle from: gradle-wrapper.properties**. El proyecto fija Gradle 8.7, compatible con el Android Gradle Plugin incluido.
3. Espere a que Gradle descargue las dependencias.
4. Ejecute `app` en un emulador o teléfono con Android 14 (API 34).

La app no solicita permisos ni transmite datos: todo queda en la base de datos local del teléfono.
