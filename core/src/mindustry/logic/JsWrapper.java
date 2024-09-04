package mindustry.logic;

import arc.util.*;
import arc.util.Time;
import mindustry.gen.*;
import mindustry.logic.JsExecutor;
import mindustry.logic.LExecutor.*;
import mindustry.logic.LStatements.*;
import mindustry.game.Team;
import mindustry.world.blocks.logic.LogicBlock.*;
import mindustry.world.blocks.logic.LogicDisplay.*;
import mindustry.world.blocks.logic.LogicDisplay.GraphicsType;
import mindustry.world.meta.BlockFlag;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class JsWrapper {
    private JsExecutor executor;
    public CPU cpu;
    public JsExecutor.Console console;

    public JsWrapper(JsExecutor executor, JsExecutor.Console console, Scriptable scope) {
        this.executor = executor;
        this.console = console;
        cpu = new CPU(executor);

        ScriptableObject.putProperty(scope, "cpu", Context.javaToJS(cpu, scope));
        ScriptableObject.putProperty(scope, "console", Context.javaToJS(console, scope));

    }

    public class CPU extends JsBuilding {
        private final LogicBuild logicBuild;
        public final JsCanvas canvas;
        public final JsMessage message;

        public CPU(JsExecutor executor) {
            super(executor.thisv);
            logicBuild = (LogicBuild) executor.thisv.objval;
            canvas = new JsCanvas(executor);
            message = new JsMessage(executor);
        }

        public Scriptable links() {
            Scriptable object = executor.context.newObject(executor.scope);
            for (LogicLink link : logicBuild.links) {
                if (link.lastBuild != null) {
                    JsBuilding generic = new JsBuilding(link.lastBuild);
                    Object genericWrapped = Context.javaToJS(generic, executor.scope);
                    ScriptableObject.putProperty(object, link.name, genericWrapped);
                }
            }
            return object;
        }

        public Scriptable linkArray() {
            Object[] links = logicBuild.links
                    .select(link -> link.lastBuild != null)
                    .map(link -> Context.javaToJS(new JsBuilding(link.lastBuild), executor.scope))
                    .toArray(Object.class);
            Scriptable object = executor.context.newArray(executor.scope, links);
            return object;
        }

        public Boolean linkNameIsValid(String linkName) {
            LogicLink logicLink = logicBuild.links.find(l -> l.name == linkName);
            return logicLink != null;
        }

        public JsBuilding getLink(String linkName) {
            LogicLink logicLink = logicBuild.links.find(l -> l.name == linkName);
            if (logicLink != null && logicLink.lastBuild != null) {
                return new JsBuilding(logicLink.lastBuild);
            }
            return null;
        }

        public String[] getLinkNames() {
            return logicBuild.links.map(link -> (String) link.name).toArray(String.class);
        }

        public JsBuilding[] getLinkArray() {
            return logicBuild.links
                    .select(link -> link.lastBuild != null && !link.lastBuild.dead())
                    .map(link -> new JsBuilding(link.lastBuild)).toArray(JsBuilding.class);
        }

        public JsUnit bind(String unitType) {
            LExecutor.UnitBindI unitBind = new LExecutor.UnitBindI(executor.builder.var(unitType));
            unitBind.run(executor);
            if (executor.unit != null) {
                return new JsUnit((Unit) executor.unit.objval);
            }
            return null;
        }

        public JsUnit bind(JsUnit unit) {
            LExecutor.UnitBindI unitBind = new LExecutor.UnitBindI(unit.target);
            unitBind.run(executor);
            if (executor.unit != null) {
                return new JsUnit((Unit) executor.unit.objval);
            }
            return null;
        }

        public void sleep(Number milliseconds) {
            executor.sleepUntil = Time.nanos() + Time.millisToNanos(milliseconds.longValue());
            executor.sendToYield();
        }

        public void yield() {
            executor.sendToYield();
        }
    }

    public class JsBuilding extends JsGeneric {

        public JsBuilding(Building b) {
            super(b);
        }

        public JsBuilding(JsBuilding original) {
            super(original);
        }

        public JsBuilding(LVar lvar) {
            super(lvar);
        }

    }

    public class JsGeneric {
        protected LVar ret = new LVar("ret");
        protected LVar p1 = new LVar("p1");
        protected LVar p2 = new LVar("p2");
        protected LVar p3 = new LVar("p3");
        protected LVar p4 = new LVar("p4");
        protected LVar p5 = new LVar("p5");
        protected LVar p6 = new LVar("p6");
        protected final LVar target;

        public JsGeneric(JsGeneric original) {
            target = original.target;
        }

        public JsGeneric(LVar lvar) {
            target = lvar;
        }

        public JsGeneric(String linkName) {
            LogicBuild logicBuild = (LogicBuild) executor.thisv.objval;
            LogicLink logicLink = logicBuild.links.find(l -> l.name == linkName);
            target = new LVar(logicLink.name);
            target.setobj(logicLink.lastBuild);
        }

        public JsGeneric(Building b) {
            target = new LVar("JsBuilding");
            target.setobj(b);
        }

        public JsGeneric(Unit u) {
            target = new LVar("JsUnit");
            target.setobj(u);
        }

        public Object sensor(String senseableType) {
            cpu.yield();
            LExecutor.SenseI sense = new LExecutor.SenseI(target, ret, executor.builder.var(senseableType));
            sense.run(executor);
            return ret.num();
        }

        public void shoot(Number x, Number y, Boolean shoot) {
            cpu.yield();
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            p3.setbool(shoot);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.shoot, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public void shootp(JsUnit unit, Boolean shoot) {
            cpu.yield();
            p1.setobj(unit.target);
            p2.setbool(shoot);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.shootp, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public void color(String color) {
            cpu.yield();
            p1.setnum(0);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.color, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public void setConfig(Object config) {
            cpu.yield();
            p1.setnum(0);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.config, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public void setEnabled(Boolean value) {
            cpu.yield();
            p1.setbool(value);
            LExecutor.ControlI control = new LExecutor.ControlI(LAccess.enabled, target, p1, p2, p3, p4);
            control.run(executor);
        }

        public Number read(Number address) {
            cpu.yield();
            p1.setnum(address.doubleValue());
            LExecutor.ReadI read = new LExecutor.ReadI(target, p1, ret);
            read.run(executor);
            return ret.num();
        }

        public void write(Number address, Number value) {
            cpu.yield();
            p1.setnum(address.doubleValue());
            p2.setnum(value.doubleValue());
            LExecutor.WriteI write = new LExecutor.WriteI(target, p1, p2);
            write.run(executor);
        }

        public Object radar(String targetType1, String targetType2, String targetType3, Number order, String sort) {
            cpu.yield();
            p1.setnum(order.doubleValue());
            p3.setobj(executor.builder.var(targetType3));
            LExecutor.RadarI radar = new LExecutor.RadarI(RadarTarget.valueOf(targetType1),
                    RadarTarget.valueOf(targetType2),
                    RadarTarget.valueOf(targetType3), RadarSort.valueOf(sort), ret, p1, ret);
            radar.run(executor);
            if (ret.isobj) {
                if (ret.obj() instanceof Building b) {
                    return new JsBuilding(b);
                }
                if (ret.obj() instanceof Unit u) {
                    if (u.team == Team.derelict)
                        return new JsUnit(u);
                }
            }
            return null;
        }

        public void flush(){
            cpu.yield();
            console.log("flush: Buffer at start: " + String.valueOf(executor.graphicsBuffer.size));
            if(target.building() instanceof LogicDisplayBuild d && (d.team == executor.team || executor.privileged)){
                if(d.commands.size + executor.graphicsBuffer.size < executor.maxDisplayBuffer){
                    console.log("flush: should execute");
                }else{
                    console.log("flush: Buffer too full");
                }
            }else{
                console.log("flush: cannot execute");
            }
            LExecutor.DrawFlushInst drawFlush = new LExecutor.DrawFlushInst(target);
            drawFlush.run(executor);
            console.log("flush: Buffer at end: " + String.valueOf(executor.graphicsBuffer.size));
        }

        public String toString() {
            return PrintI.toString(target.objval);
        }
    }

    public class JsUnit extends JsGeneric {
        public JsUnit(LVar lvar) {
            super(lvar);
        }

        public JsUnit(Unit u) {
            super(u);
        }

        private void control(LUnitControl type) {
            cpu.yield();
            if (executor.unit.objval != target.objval)
                cpu.bind(this);
            LExecutor.UnitControlI unitControl = new LExecutor.UnitControlI(type, p1, p2, p3, p4, p5);
            unitControl.run(executor);
        }

        public void idle() {
            control(LUnitControl.idle);
        }

        public void move(Number x, Number y) {
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            control(LUnitControl.move);
        }

        public void pathfind(Number x, Number y) {
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            control(LUnitControl.pathfind);
        }

        public void approach(Number x, Number y) {
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            control(LUnitControl.approach);
        }

        public void autoPathFind() {
            control(LUnitControl.autoPathfind);
        }

        public void stop() {
            control(LUnitControl.stop);
        }

        public Boolean within(Number x, Number y, Number radius) {
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            p3.setnum(radius.doubleValue());
            control(LUnitControl.within);
            return p4.bool();
        }

        public void boost(Boolean value) {
            p1.setbool(value);
            control(LUnitControl.within);
        }

        public void target(Number x, Number y, Boolean shoot) {
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            p3.setbool(shoot);
            control(LUnitControl.target);
        }

        public void targetp(JsUnit unit, Boolean shoot) {
            p1.setobj(unit.target.objval);
            p2.setbool(shoot);
            control(LUnitControl.targetp);
        }

        public void itemTake(JsBuilding fromBuilding, String itemType, Number amount) {
            p1.setobj(fromBuilding.target.objval);
            p2.setobj(executor.builder.var(itemType));
            p3.setnum(amount.intValue());
            control(LUnitControl.itemTake);
        }

        public void itemDrop(JsBuilding toBuilding, Number amount) {
            p1.setobj(toBuilding.target.objval);
            p2.setnum(amount.intValue());
            control(LUnitControl.itemDrop);
        }

        public void payloadTakeUnit() {
            p1.setbool(true);
            control(LUnitControl.payTake);
        }

        public void payloadTakeBlock() {
            p1.setbool(false);
            control(LUnitControl.payTake);
        }

        public void payloadDrop() {
            control(LUnitControl.payDrop);
        }

        public void payloadEnter() {
            control(LUnitControl.payEnter);
        }

        public void mine(Number x, Number y) {
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            control(LUnitControl.mine);
        }

        public void build(Number x, Number y, String blockType, Number rotation, String config) {
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            p3.setobj(executor.builder.var(blockType));
            p4.setnum(rotation.intValue());
            p5.setobj(executor.builder.var(config));
            control(LUnitControl.build);
        }

        public void flag(Number value) {
            p1.setnum(value.doubleValue());
            control(LUnitControl.flag);
        }

        public GetBlockResult getBlock(Number x, Number y) {
            p1.setnum(x.doubleValue());
            p2.setnum(y.doubleValue());
            control(LUnitControl.getBlock);

            // {Number type, Building building, Number floorType}
            JsBuilding building = null;
            if (p5.isobj && p5.objval instanceof Building b) {
                building = new JsBuilding(b);
            }
            return new GetBlockResult(p3.objval.toString(), building, p5.objval.toString());
        }

        private LocateResult locate(LLocate type) {
            return locate(type, BlockFlag.battery);
        }

        private LocateResult locate(LLocate type, BlockFlag blockFlag) {
            cpu.yield();
            if (executor.unit.objval != target.objval)
                cpu.bind(this);
            LExecutor.UnitLocateI unitLocate = new LExecutor.UnitLocateI(LLocate.building, blockFlag, p1, p1, p2, p3,
                    p4, ret);
            unitLocate.run(executor);
            if (p4.bool()) {
                if (ret.objval instanceof Building b) {
                    JsBuilding building = new JsBuilding(b);
                    return new LocateResult(p1.numval, p2.numval, building);
                } else {
                    return new LocateResult(p1.numval, p2.numval);
                }
            }
            return null;
        }

        public LocateResult locateBuilding(String group, Boolean enemy) {
            p1.setbool(enemy);
            return locate(LLocate.building, BlockFlag.valueOf(group));
        }

        public LocateResult locateOre(String oreType) {
            p1.setobj(executor.builder.var(oreType));
            return locate(LLocate.ore);
        }

        public Object locateSpawn() {
            return locate(LLocate.spawn);
        }

        public Object locateDamaged() {
            return locate(LLocate.damaged);
        }

        public void unbind() {
            if (executor.unit.objval != target.objval)
                cpu.bind(this);
            control(LUnitControl.unbind);
        }

        public class GetBlockResult {
            public String type, floor;
            public JsBuilding building;

            public GetBlockResult(String type, JsBuilding building, String floor) {
                this.building = building;
                this.type = type;
                this.floor = floor;
            }
        }

        public class LocateResult {
            public Number x, y;
            public JsBuilding building;

            public LocateResult(double xval, double yval) {
                x = xval;
                y = yval;
                building = null;
            }

            public LocateResult(double xval, double yval, JsBuilding building) {
                x = xval;
                y = yval;
                this.building = building;
            }
        }
    }

    public class JsMessage{
        private final LExecutor executor;
        protected LVar p1 = new LVar("p1");
        
        JsMessage(LExecutor executor){
            this.executor = executor;
        }

        public void print(String text){
            cpu.yield();
            p1.setobj(text);
            LExecutor.PrintI print = new LExecutor.PrintI(p1);
            print.run(executor);
        }

        public void flush(JsBuilding message){
            cpu.yield();
            p1.setobj(message.target);
            LExecutor.PrintFlushI printFlush = new LExecutor.PrintFlushI(p1);
            printFlush.run(executor);
        }

        public String format(Object object){
            return PrintI.toString(object);
        }
    }

    public class JsCanvas{
        protected LVar p1 = new LVar("p1");
        protected LVar p2 = new LVar("p2");
        protected LVar p3 = new LVar("p3");
        protected LVar p4 = new LVar("p4");
        protected LVar p5 = new LVar("p5");
        protected LVar p6 = new LVar("p6");

        private final LExecutor executor;

        JsCanvas(LExecutor executor){
            this.executor = executor;
        }

        public void draw(GraphicsType type){
            cpu.yield();
            DrawInst draw = new DrawInst((byte) type.ordinal(), p1, p2, p3, p4, p5, p6);
            draw.run(executor);
        }

        public void clear(Number r, Number g, Number b){
            p1.setnum(r.longValue());
            p2.setnum(g.longValue());
            p3.setnum(b.longValue());
            draw(GraphicsType.clear);
        }
        
        
        public void color(Number r, Number g, Number b, Number a){
            p1.setnum(r.doubleValue());
            p2.setnum(g.doubleValue());
            p3.setnum(b.doubleValue());
            p4.setnum(a.doubleValue());
            draw(GraphicsType.color);
        }
        
        
        /*public void col(String color){
            p1.setnum(r.longValue());
            p2.setnum(g.longValue());
            p3.setnum(b.longValue());
            p4.setnum(a.longValue());
            draw(GraphicsType.col);
        }*/
        
        
        public void stroke(Number width){
            p1.setnum(width.doubleValue());
            draw(GraphicsType.stroke);
        }
        
        
        public void line(double x, double y, double x2, double y2){
            p1.setnum(x);
            p2.setnum(y);
            p3.setnum(x2);
            p4.setnum(y2);
            draw(GraphicsType.line);
        }
        
        
        public void rect(Number x, Number y, Number x2, Number y2){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());
            p3.setnum(x2.longValue());
            p4.setnum(y2.longValue());
            draw(GraphicsType.rect);
        }
        
        
        public void lineRect(Number x, Number y, Number x2, Number y2){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());
            p3.setnum(x2.longValue());
            p4.setnum(y2.longValue());
            draw(GraphicsType.lineRect);
        }
        
        
        public void poly(Number x, Number y, Boolean sides, Number radius, Number rotation){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());
            p3.setbool(sides);
            p4.setnum(radius.longValue());
            p5.setnum(rotation.longValue());
            draw(GraphicsType.poly);        
        }
        
        
        public void linePoly(Number x, Number y, Boolean sides, Number radius, Number rotation){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());
            p3.setbool(sides);
            p4.setnum(radius.longValue());
            p5.setnum(rotation.longValue());
            draw(GraphicsType.linePoly);        
        }
        
        
        public void triangle(Number x, Number y, Number x2, Number y2, Number x3, Number y3){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());
            p3.setnum(x2.longValue());
            p4.setnum(y2.longValue());
            p5.setnum(x3.longValue());
            p6.setnum(y3.longValue());
            draw(GraphicsType.triangle);                
        }
        
        
        public void image(Number x, Number y, String image, Number size, Number rotation){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());            
            p3.setobj(((JsExecutor) executor).builder.var(image).objval);
            p4.setnum(size.longValue());
            p5.setnum(rotation.longValue());
            draw(GraphicsType.image);                
        }
        
        
        public void print(String text, Number x, Number y, String align){
            executor.textBuffer.append(text);
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());            
            p3.setnum(DrawStatement.nameToAlign.get(align, Align.bottomLeft));
            draw(GraphicsType.print);                  
        }
        
        
        public void translate(Number x, Number y){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());            
            draw(GraphicsType.print);           
        }
        
        
        public void scale(Number x, Number y){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());            
            draw(GraphicsType.scale);    
        }
    
    
        public void rotate(Number x, Number y){
            p1.setnum(x.longValue());
            p2.setnum(y.longValue());            
            draw(GraphicsType.rotate);           
        }
    }
}