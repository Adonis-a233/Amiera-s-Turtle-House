@echo off
echo 开始打包项目...
echo.

REM 清理旧的构建文件
echo 清理旧的构建文件...
mvn clean

REM 打包项目（跳过测试）
echo.
echo 正在打包项目（跳过测试）...
mvn package -DskipTests

echo.
echo 打包完成！
echo JAR文件位置: target\community-0.0.1-SNAPSHOT.jar
echo.
echo 运行命令: java -jar target\community-0.0.1-SNAPSHOT.jar
pause