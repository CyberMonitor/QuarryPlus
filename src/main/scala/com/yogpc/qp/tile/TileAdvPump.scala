package com.yogpc.qp.tile

import java.lang.{Boolean => JBool, Byte => JByte, Integer => JInt}

import com.yogpc.qp.block.ADismCBlock
import com.yogpc.qp.compat.{INBTReadable, INBTWritable}
import com.yogpc.qp.tile.IEnchantableTile.{EfficiencyID, FortuneID, SilktouchID, UnbreakingID}
import com.yogpc.qp.tile.TileAdvPump._
import com.yogpc.qp.{Config, QuarryPlus, QuarryPlusI}
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.{BlockPos, ChunkPos}
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.{EnumFacing, ITickable}
import net.minecraftforge.common.ForgeChunkManager
import net.minecraftforge.common.ForgeChunkManager.Type
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fluids.capability.{CapabilityFluidHandler, FluidTankProperties, IFluidHandler, IFluidTankProperties}
import net.minecraftforge.fluids.{Fluid, FluidRegistry, FluidStack, FluidUtil, IFluidBlock}
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @see [[buildcraft.factory.tile.TilePump]]
  */
class TileAdvPump extends APowerTile with IEnchantableTile with ITickable with IDebugSender {

    var placeFrame = true
    var delete = false
    private[this] var finished = true
    private[this] var toStart = Config.content.pumpAutoStart
    private[this] var queueBuilt = false
    private[this] var skip = false
    private[this] var ench = TileAdvPump.defaultEnch
    private[this] var target: BlockPos = BlockPos.ORIGIN
    private[this] var toDig: List[BlockPos] = Nil
    private[this] var inRange: Set[BlockPos] = Set.empty
    private[this] val paths = mutable.Map.empty[BlockPos, List[BlockPos]]
    private[this] val FACINGS = List(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST)

    override def isWorking = !finished

    override def G_reinit() = {
        configure(128d, 1024d)
        finished = true
        queueBuilt = false
        skip = false
        target = BlockPos.ORIGIN
        toDig = Nil
        paths.clear()
    }

    override def update() = {
        super.update()
        if (!getWorld.isRemote) {
            if (finished) {
                if (toStart) {
                    toStart = false
                    buildWay()
                    if (toDig.nonEmpty) {
                        finished = false
                        val state = getWorld.getBlockState(getPos)
                        if (!state.getValue(ADismCBlock.ACTING)) {
                            validate()
                            getWorld.setBlockState(getPos, state.withProperty(ADismCBlock.ACTING, JBool.TRUE))
                            validate()
                            getWorld.setTileEntity(getPos, this)
                        }
                    }
                }
            } else {
                if (!queueBuilt) {
                    buildWay()
                    queueBuilt = true
                }
                if (skip) {
                    skip = false
                    nextPos()
                } else {
                    if (target == BlockPos.ORIGIN) {
                        buildWay()
                        nextPos()
                    }
                    pump()
                }
            }
            push()
        }
    }

    def nextPos(i: Int = 0): Unit = {
        toDig match {
            case next :: rest => toDig = rest
                if (TilePump.isLiquid(getWorld.getBlockState(next), true, getWorld, next)) {
                    target = next
                } else {
                    if (i > ench.maxAmount / 1000) {
                        skip = true
                        if (Config.content.debug) {
                            QuarryPlus.LOGGER.warn("Pump overflow", new StackOverflowError("Pump"))
                        }
                    } else
                        nextPos(i + 1)
                }
            case Nil => target = BlockPos.ORIGIN
        }
    }

    def buildWay(): Unit = {
        val checked = mutable.Set.empty[BlockPos]
        val nextPosesToCheck = new ListBuffer[BlockPos]()
        var fluid = FluidRegistry.WATER
        toDig = Nil
        paths.clear()

        getWorld.theProfiler.startSection("Depth")
        Iterator.iterate(getPos.down())(_.down()).takeWhile(pos => pos.getY > 0 && ench.inRange(getPos, pos))
          .find(!getWorld.isAirBlock(_)).foreach(pos => {
            val state = getWorld.getBlockState(pos)
            if (TilePump.isLiquid(state, false, getWorld, pos)) {
                checked.add(pos)
                paths.put(pos, List(pos))
                if (TilePump.isLiquid(state, true, getWorld, pos))
                    toDig = pos :: toDig
                nextPosesToCheck += pos

                fluid = findFluid(state)
            }
        })
        if (nextPosesToCheck.isEmpty) {
            finished = true
            queueBuilt = false
            val state = getWorld.getBlockState(getPos)
            validate()
            getWorld.setBlockState(getPos, state.withProperty(ADismCBlock.ACTING, JBool.FALSE))
            validate()
            getWorld.setTileEntity(getPos, this)
            getWorld.theProfiler.endSection()
            return
        }

        getWorld.theProfiler.endStartSection("Wide")
        while (nextPosesToCheck.nonEmpty) {
            val copied = nextPosesToCheck.result()
            nextPosesToCheck.clear()
            for (posToCheck <- copied; offset <- FACINGS) {
                val offsetPos = posToCheck.offset(offset)
                if (ench.inRange(getPos, offsetPos)) {
                    if (checked.add(offsetPos)) {
                        val state = getWorld.getBlockState(offsetPos)
                        if (findFluid(state) == fluid) {
                            paths.put(offsetPos, offsetPos :: paths(posToCheck))
                            nextPosesToCheck += offsetPos
                            if (TilePump.isLiquid(state, true, getWorld, offsetPos))
                                toDig = offsetPos :: toDig
                        }
                    } //else means the pos has already checked.
                }
            }
        }
        inRange = checked.toSet
        getWorld.theProfiler.endSection()
    }

    def pump(): Unit = {
        var break = false
        while (FluidHandler.canPump && !break) {
            val option = paths.get(target)
            if (option.nonEmpty) {
                val energy = ench.getEnergy(placeFrame)
                if (useEnergy(energy, energy, true) == energy) {
                    val maybeStack = Option(FluidUtil.getFluidHandler(getWorld, target, EnumFacing.UP)).map(_.drain(Fluid.BUCKET_VOLUME, false))
                    if (maybeStack.isDefined && option.get.forall(pos => TilePump.isLiquid(getWorld.getBlockState(pos), false, getWorld, pos) /*&&
                      (FluidHandler.getFluidType == null || FluidHandler.getFluidType == maybeStack.get.getFluid)*/)) {
                        FluidHandler.fill(maybeStack, doFill = true)
                        replaceFluidBlock(target)
                        nextPos()
                    } else {
                        buildWay()
                        nextPos()
                    }
                } else {
                    //Pump can't act because of lack of energy. Wait to get more energy.
                    break = true
                }
            } else {
                buildWay()
                nextPos()
                break = true
            }
        }
    }

    def push(): Unit = {
        if (delete) {
            FluidHandler.drain(FluidHandler.getAmount, doDrain = true)
            return
        }
        val stack = FluidHandler.drain(FluidHandler.getAmount, doDrain = false)
        if (!stack.isEmpty) {
            EnumFacing.VALUES.map(f => (getWorld.getTileEntity(getPos.offset(f)), f))
              .collect { case (t, f) if t != null => t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite) }
              .filter(_ != null).foreach(handler => {
                if (stack.amount > 0) {
                    val used = handler.fill(stack, true)
                    if (used > 0) {
                        FluidHandler.drain(stack.copy().setAmount(used), doDrain = true)
                    }
                }
            })
        }
    }

    def start(): Unit = {
        if (!isWorking)
            toStart = true
    }

    def replaceFluidBlock(pos: BlockPos): Unit = {
        FluidUtil.getFluidHandler(getWorld, pos, EnumFacing.UP).drain(Fluid.BUCKET_VOLUME, true)
        if (placeFrame)
            EnumFacing.HORIZONTALS.map(pos.offset).filter(!inRange.contains(_)).foreach(offset => {
                if (TilePump.isLiquid(getWorld.getBlockState(offset), false, getWorld, offset)) {
                    getWorld.setBlockState(offset, QuarryPlusI.blockFrame.getDamiingState)
                }
            })
    }

    private def findFluid(state: IBlockState) = {
        if (state.getBlock == Blocks.FLOWING_WATER) {
            FluidRegistry.WATER
        } else if (state.getBlock == Blocks.FLOWING_LAVA) {
            FluidRegistry.LAVA
        } else {
            state.getBlock match {
                case fluidBlock: IFluidBlock => FluidRegistry.getFluid(fluidBlock.getFluid.getName)
                case block => FluidRegistry.lookupFluidForBlock(block)
            }
        }
    }

    /**
      * @return Map (Enchantment id, level)
      */
    override def getEnchantments = ench.getMap.collect { case (a, b) if b > 0 => (JInt.valueOf(a), JByte.valueOf(b)) }.asJava

    /**
      * @param id    Enchantment id
      * @param value level
      */
    override def setEnchantent(id: Short, value: Short) = ench = ench.set(id, value)

    override def getDebugName = "tile.standalonepump.name"

    override def getDebugmessages = {
        List("Range = " + ench.distance,
            "Finished : " + finished,
            "Ench : " + ench,
            "FluidType : " + Option(FluidHandler.getFluidType).map(_.getName).getOrElse("None"),
            "FluidAmount : " + FluidHandler.getAmount,
            "Pumped : " + FluidHandler.amountPumped,
            "Delete : " + delete).map(new TextComponentString(_)).asJava
    }

    override def readFromNBT(nbttc: NBTTagCompound) = {
        val NBT_POS = "targetpos"
        val NBT_FINISHED = "finished"
        val NBT_PLACEFRAME = "placeFrame"
        val NBT_DELETE = "delete"
        super.readFromNBT(nbttc)
        target = BlockPos.fromLong(nbttc.getLong(NBT_POS))
        ench = PEnch.readFromNBT(nbttc)
        finished = nbttc.getBoolean(NBT_FINISHED)
        placeFrame = nbttc.getBoolean(NBT_PLACEFRAME)
        delete = nbttc.getBoolean(NBT_DELETE)
    }

    override def writeToNBT(nbttc: NBTTagCompound) = {
        val NBT_POS = "targetpos"
        val NBT_FINISHED = "finished"
        val NBT_PLACEFRAME = "placeFrame"
        val NBT_DELETE = "delete"
        nbttc.setLong(NBT_POS, target.toLong)
        nbttc.setBoolean(NBT_FINISHED, finished)
        nbttc.setBoolean(NBT_PLACEFRAME, placeFrame)
        nbttc.setBoolean(NBT_DELETE, delete)
        ench.writeToNBT(nbttc)
        super.writeToNBT(nbttc)
    }

    override def hasCapability(capability: Capability[_], facing: EnumFacing) = {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) true
        else super.hasCapability(capability, facing)
    }

    override def getCapability[T](capability: Capability[T], facing: EnumFacing) = {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(FluidHandler)
        else super.getCapability(capability, facing)
    }

    override def onLoad(): Unit = super.onLoad()

    private[this] var chunkTicket: ForgeChunkManager.Ticket = _

    def requestTicket(): Unit = {
        if (this.chunkTicket != null) return
        this.chunkTicket = ForgeChunkManager.requestTicket(QuarryPlus.INSTANCE, getWorld, Type.NORMAL)
        if (this.chunkTicket == null) return
        val tag = this.chunkTicket.getModData
        tag.setInteger("quarryX", getPos.getX)
        tag.setInteger("quarryY", getPos.getY)
        tag.setInteger("quarryZ", getPos.getZ)
        forceChunkLoading(this.chunkTicket)
    }

    def forceChunkLoading(ticket: ForgeChunkManager.Ticket): Unit = {
        if (this.chunkTicket == null) this.chunkTicket = ticket
        val quarryChunk = new ChunkPos(getPos)
        ForgeChunkManager.forceChunk(ticket, quarryChunk)
    }

    override def onChunkUnload(): Unit = {
        ForgeChunkManager.releaseTicket(this.chunkTicket)
        super.onChunkUnload()
    }

    @SideOnly(Side.CLIENT)
    def recieveStatusMessage(placeFrame: Boolean, nbt: NBTTagCompound): Unit = {
        this.placeFrame = placeFrame
        this.readFromNBT(nbt)
    }

    private object FluidHandler extends IFluidHandler {

        private[this] val fluidStacks = new ListBuffer[FluidStack]
        var amountPumped = 0l

        override def fill(resource: FluidStack, doFill: Boolean): Int = 0

        def fillInternal(resource: FluidStack, doFill: Boolean): Int = {
            fluidStacks.find(_ == resource) match {
                case Some(stack) =>
                    val nAmount = stack.amount + resource.amount
                    if (nAmount <= ench.maxAmount) {
                        if (doFill) {
                            stack.amount = nAmount
                            amountPumped += resource.amount
                        }
                        resource.amount
                    } else {
                        if (doFill) {
                            stack.amount = ench.maxAmount
                            amountPumped += nAmount - ench.maxAmount
                        }
                        nAmount - stack.amount
                    }
                case None =>
                    if (doFill) {
                        fluidStacks += resource.copy()
                        amountPumped += resource.amount
                    }
                    resource.amount
            }
        }

        def fill(mayStack: Option[FluidStack], doFill: Boolean): Int = {
            mayStack match {
                case Some(stack) => fillInternal(stack, doFill)
                case None => 0
            }
        }

        override def drain(resource: FluidStack, doDrain: Boolean): FluidStack = {
            fluidStacks.find(_ == resource) match {
                case None => null
                case Some(stack) => drainInternal(resource, stack, doDrain)
            }
        }

        override def drain(maxDrain: Int, doDrain: Boolean): FluidStack = {
            fluidStacks.headOption match {
                case None => null
                case Some(stack) => drainInternal(stack.copywithAmount(maxDrain), stack, doDrain)
            }
        }

        private def drainInternal(kind: FluidStack, source: FluidStack, doDrain: Boolean): FluidStack = {
            if (kind.amount <= 0) {
                return null
            }
            if (kind.amount >= source.amount) {
                val extract = source.amount
                if (doDrain) fluidStacks.remove(fluidStacks.indexOf(kind))
                val c = kind.copy()
                c.setAmount(extract)
                c
            } else {
                val nAmount = source.amount - kind.amount
                if (doDrain) source.setAmount(nAmount)
                kind
            }
        }

        override def getTankProperties: Array[IFluidTankProperties] = {
            if (fluidStacks.isEmpty) {
                Array(new FluidTankProperties(null, 0, false, false))
            } else {
                fluidStacks.map(s => new FluidTankProperties(s, ench.maxAmount, false, true)).toArray
            }
        }

        def getFluidType =
            if (fluidStacks.isEmpty) {
                null
            } else {
                fluidStacks.head.getFluid
            }

        def getAmount =
            if (fluidStacks.nonEmpty) fluidStacks.head.amount
            else 0

        def canPump =
            if (fluidStacks.isEmpty) true
            else fluidStacks.forall(_.amount <= ench.maxAmount / 2)
    }

}

object TileAdvPump {

    private val NBT_PENCH = "nbt_pench"
    private[this] val defaultBaseEnergy = Seq(10, 8, 6, 4)
    val defaultEnch = PEnch(efficiency = 0, unbreaking = 0, fortune = 0, silktouch = false)

    case class PEnch(efficiency: Byte, unbreaking: Byte, fortune: Byte, silktouch: Boolean) extends INBTWritable {
        require(efficiency >= 0)
        require(unbreaking >= 0)
        require(fortune >= 0)

        override def writeToNBT(nbt: NBTTagCompound): NBTTagCompound = {
            val t = new NBTTagCompound
            t.setByte("efficiency", efficiency)
            t.setByte("unbreaking", unbreaking)
            t.setByte("fortune", fortune)
            t.setBoolean("silktouch", silktouch)
            nbt.setTag(NBT_PENCH, t)
            nbt
        }

        def getMap = Map(EfficiencyID -> efficiency, UnbreakingID -> unbreaking,
            FortuneID -> fortune, SilktouchID -> silktouch.compare(false).toByte)

        def set(id: Short, level: Int): PEnch = {
            id match {
                case EfficiencyID => this.copy(efficiency = level.toByte)
                case UnbreakingID => this.copy(unbreaking = level.toByte)
                case FortuneID => this.copy(fortune = level.toByte)
                case SilktouchID => this.copy(silktouch = level > 0)
                case _ => this
            }
        }

        val distance = fortune match {
            case 0 | 1 => 64
            case 2 => 96
            case 3 => 128
        }

        val distanceSq = distance * distance

        val maxAmount = 128 * Fluid.BUCKET_VOLUME * (efficiency + 1)

        def getEnergy(placeFrame: Boolean): Double = {
            defaultBaseEnergy(if (unbreaking >= 3) 3 else unbreaking) * (if (placeFrame) 2.5 else 1)
        }

        def inRange(tilePos: BlockPos, pos: BlockPos): Boolean = {
            if (silktouch) {
                val dx = tilePos.getX - pos.getX
                val dz = tilePos.getZ - pos.getZ
                (dx * dx + dz * dz) <= distanceSq
            } else {
                tilePos.distanceSq(pos) <= distanceSq
            }
        }
    }

    object PEnch extends INBTReadable[PEnch] {
        override def readFromNBT(tag: NBTTagCompound): PEnch = {
            if (tag.hasKey(NBT_PENCH)) {
                val t = tag.getCompoundTag(NBT_PENCH)
                PEnch(t.getByte("efficiency"), t.getByte("unbreaking"), t.getByte("fortune"), t.getBoolean("silktouch"))
            } else
                PEnch(efficiency = 0, unbreaking = 0, fortune = 0, silktouch = false)
        }
    }

    implicit class FluidStackHelper(val fluidStack: FluidStack) extends AnyVal {

        def copywithAmount(amount: Int): FluidStack = {
            val copied = fluidStack.copy()
            copied.amount = amount
            copied
        }

        def setAmount(amount: Int): FluidStack = {
            fluidStack.amount = amount
            fluidStack
        }

        def isEmpty: Boolean = {
            fluidStack == null || fluidStack.amount <= 0
        }
    }

}