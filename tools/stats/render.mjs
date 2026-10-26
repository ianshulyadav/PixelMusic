import { Resvg } from '@resvg/resvg-js';
import fs from 'fs';
const [,, inp, out, width] = process.argv;
const r = new Resvg(fs.readFileSync(inp), { fitTo: width ? { mode: 'width', value: +width } : { mode: 'zoom', value: 1 } });
fs.writeFileSync(out, r.render().asPng());
