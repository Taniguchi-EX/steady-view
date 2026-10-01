# Steady View

酔いやすい人向けのMinecraft（Java版26.3、Fabric）クライアントModです。

- マウスで視点が動かなくなります。視点はキーボードで45度単位に切り替わります（補間なし）
- 攻撃・採掘・設置・使用は、画面中央ではなくマウスカーソルの指す位置に対して行います
- クライアントにだけ入れるModです。サーバ側には何も入れる必要はありません

## キー割り当て（既定）

| 操作 | キー |
| --- | --- |
| 左に45度回る | Z |
| 右に45度回る | X |
| 45度上を向く | R |
| 45度下を向く | V |
| 水平に戻す | B |
| Modの有効・無効 | F8 |

「設定 > 操作設定 > キー割り当て」の「Steady View」で変更できます。

## 設定ファイル

`config/steadyview.properties`

- `enabledOnStartup`: 起動時に有効にするか（既定 `true`）
- `confineCursor`: ゲーム中、カーソルをウィンドウ内に閉じ込めるか（既定 `true`）
- `hideCrosshair`: 有効なとき、画面中央の照準を隠すか（既定 `true`）

## 開発

JDK 25が必要です。

```sh
export JAVA_HOME="$HOME/.jdks/jdk-25.0.4.1+1"
./gradlew build             # ビルドと単体テスト。jarは build/libs/steadyview-<version>.jar
./gradlew runClient         # 開発用クライアントの起動
./gradlew runClientGameTest # ゲーム内の自動テスト（クライアントゲームテスト）
```

## ライセンス

CC0-1.0
