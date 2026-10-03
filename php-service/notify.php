<?php
header('Content-Type: application/json');
$c=require __DIR__.'/config.php';
if($_SERVER['REQUEST_METHOD']!=='POST'){http_response_code(405);echo json_encode(['ok'=>false,'message'=>'POST required']);exit;}
$email=filter_var($_POST['email']??'',FILTER_VALIDATE_EMAIL);$name=trim($_POST['name']??'');$message=trim($_POST['message']??'');
if(!$email||$message===''){http_response_code(422);echo json_encode(['ok'=>false,'message'=>'Valid email and message required']);exit;}
$subject='Hospital duty notification';$headers="MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nFrom: hospital-operations@localhost\r\n";
$sent=@mail($email,$subject,$message,$headers);
try{require __DIR__.'/db.php';$stmt=$pdo->prepare("INSERT INTO audit_logs(user_id,action,description) VALUES(NULL,'PHP_EMAIL_SERVICE',?)");$stmt->execute(["Notification requested for $name <$email>"]);}catch(Throwable $e){}
echo json_encode(['ok'=>true,'mail_sent'=>$sent]);
