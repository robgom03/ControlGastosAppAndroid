# Control de gastos

Aplicación Android nativa en español para llevar varios presupuestos mensuales independientes en euros.

## Incluido

- Cuentas con límite y aviso de proximidad propios.
- Alta, edición y borrado de gastos del mes en curso.
- Saldo disponible destacado y aviso al acercarse o superar el límite.
- Archivado automático al cambiar de mes: conserva límite, gasto total y diferencia, y elimina los movimientos individuales cerrados.
- Historial separado por cuenta y confirmación antes de eliminar una cuenta.

## Abrir y ejecutar

1. Abra esta carpeta en Android Studio (Hedgehog o posterior).
2. En **Settings > Build, Execution, Deployment > Build Tools > Gradle**, elija **Use Gradle from: gradle-wrapper.properties**. El proyecto fija Gradle 8.7, compatible con el Android Gradle Plugin incluido.
3. Espere a que Gradle descargue las dependencias.
4. Ejecute `app` en un emulador o teléfono con Android 14 (API 34).

La app no solicita permisos ni transmite datos: todo queda en la base de datos local del teléfono.
