import { deflateSync } from 'node:zlib';

// PNG minimal, generat al servidor: icona sòlida per Android, mida 192/512.
const table=Array.from({length:256},(_,n)=>{
  let c=n;
  for(let k=0;k<8;k++)c=(c&1)?(0xedb88320^(c>>>1)):(c>>>1);
  return c>>>0;
});
function crc32(buf){
  let crc=0xffffffff;
  for(const byte of buf)crc=table[(crc^byte)&255]^(crc>>>8);
  return (crc^0xffffffff)>>>0;
}
function chunk(type,bytes){
  const name=Buffer.from(type,'ascii'),size=Buffer.alloc(4),end=Buffer.alloc(4);
  size.writeUInt32BE(bytes.length);
  end.writeUInt32BE(crc32(Buffer.concat([name,bytes])));
  return Buffer.concat([size,name,bytes,end]);
}
function inside(x,y){
  // Teulada i cos de la casa.
  const roof=y>=.25&&y<=.52&&Math.abs(x-.50)<(y-.25)*1.28;
  const house=x>=.265&&x<=.735&&y>=.47&&y<=.78;
  const door=x>=.435&&x<=.565&&y>=.59&&y<=.78;
  const sun=(x-.76)**2+(y-.275)**2<=.062**2;
  return {roof,house,door,sun};
}
function generate(size){
  const width=size,raw=Buffer.alloc((width*4+1)*width);
  for(let y=0;y<width;y++){
    const offset=y*(width*4+1);raw[offset]=0;
    for(let x=0;x<width;x++){
      const normalizedX=(x+.5)/width,normalizedY=(y+.5)/width;
      const {roof,house,door,sun}=inside(normalizedX,normalizedY);
      const color=sun?[233,180,119]:door?[33,76,60]:(house||roof)?[245,246,241]:[33,76,60];
      const idx=offset+1+x*4;
      raw[idx]=color[0];raw[idx+1]=color[1];raw[idx+2]=color[2];raw[idx+3]=255;
    }
  }
  const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(width,0);ihdr.writeUInt32BE(width,4);ihdr[8]=8;ihdr[9]=6;
  return Buffer.concat([
    Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ihdr),
    chunk('IDAT',deflateSync(raw,{level:8})),chunk('IEND',Buffer.alloc(0))
  ]);
}
export default function handler(req,res){
  if(req.method!=='GET'){res.setHeader('Allow','GET');return res.status(405).end();}
  const size=Number(req.query?.size);
  if(size!==192&&size!==512)return res.status(400).json({error:'Mida no vàlida'});
  res.setHeader('Content-Type','image/png');
  res.setHeader('Cache-Control','public,max-age=31536000,immutable');
  res.setHeader('X-Content-Type-Options','nosniff');
  res.status(200).end(generate(size));
}
