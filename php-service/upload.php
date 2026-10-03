<?php
header('Content-Type: application/json');
if($_SERVER['REQUEST_METHOD']!=='POST'){http_response_code(405);echo json_encode(['ok'=>false]);exit;}
$staff=(int)($_POST['staff_id']??0);if($staff<1||!isset($_FILES['file'])){http_response_code(422);echo json_encode(['ok'=>false,'message'=>'staff_id and file are required']);exit;}
$f=$_FILES['file'];if($f['error']!==UPLOAD_ERR_OK||$f['size']>5*1024*1024){http_response_code(422);echo json_encode(['ok'=>false,'message'=>'Upload failed or file exceeds 5 MB']);exit;}
$ext=strtolower(pathinfo($f['name'],PATHINFO_EXTENSION));if(!in_array($ext,['jpg','jpeg','png','pdf'],true)){http_response_code(422);echo json_encode(['ok'=>false,'message'=>'Invalid file type']);exit;}
$dir=__DIR__.'/uploads';if(!is_dir($dir))mkdir($dir,0755,true);$safe=bin2hex(random_bytes(8)).'_'.preg_replace('/[^a-zA-Z0-9._-]/','_',$f['name']);move_uploaded_file($f['tmp_name'],$dir.'/'.$safe);
try{require __DIR__.'/db.php';$q=$pdo->prepare('INSERT INTO documents(staff_id,file_name,file_path,file_type) VALUES(?,?,?,?)');$q->execute([$staff,$f['name'],'php-service/uploads/'.$safe,$f['type']]);echo json_encode(['ok'=>true,'file'=>$safe]);}catch(Throwable $e){http_response_code(500);echo json_encode(['ok'=>false,'message'=>'Database error']);}
