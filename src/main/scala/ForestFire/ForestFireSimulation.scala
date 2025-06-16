package ForestFire

import scala.util.Random

object SimulationConstants:
  val baseExtinguishProbability = 0.01
  val waterExtinguishBonus = 0.1
  val humidityExtinguishBonus = 0.1
  val maxExtinguishProbability = 0.8

  val baseSelfIgnitionChance = 0.001
  val temperatureSelfIgnitionMultiplier = 2.0
  val humiditySelfIgnitionReduction = 0.5

  val neighborIgnitionEffect = 0.8
  val temperatureIgnitionBase = 25.0
  val minTemperatureEffect = 0.5
  val minHumidityEffect = 0.3

  val grassToBushChance = 0.02
  val grassToSmallTreeChance = 0.008
  val grassToTreeGrowthChance = 0.01
  val bushToSmallTreeChance = 0.015
  val smallTreeToGrowingTreeChance = 0.01
  val growingTreeToTreeChance = 0.008
  val destroyedToGrassChance = 0.01
  val destroyedToBushChance = 0.006
  val destroyedToSmallTreeChance = 0.004
  val destroyedToTreeChance = 0.005

  val grassBurnTime = 1
  val grassIgnitionProb = 0.8
  val bushBurnTime = 2
  val bushIgnitionProb = 0.7
  val smallTreeBurnTime = 3
  val smallTreeIgnitionProb = 0.6
  val growingTreeBurnTime = 4
  val growingTreeIgnitionProb = 0.5
  val treeBurnTime = 5
  val treeIgnitionProb = 0.4

case class GlobalEnvironment(temperature: Double, atmosphereHumidity: Double)

enum WindDirection:
  case North, East, South, West

sealed trait ForestItem:
  def canIgnite: Boolean
  def burnTime: Int
  def ignitionProbability: Double

case object Water extends ForestItem:
  val canIgnite = false
  val burnTime = 0
  val ignitionProbability = 0.0

case object Rock extends ForestItem:
  val canIgnite = false
  val burnTime = 0
  val ignitionProbability = 0.0

case object Grass extends ForestItem:
  val canIgnite = true
  val burnTime = SimulationConstants.grassBurnTime
  val ignitionProbability = SimulationConstants.grassIgnitionProb

case object Bush extends ForestItem:
  val canIgnite = true
  val burnTime = SimulationConstants.bushBurnTime
  val ignitionProbability = SimulationConstants.bushIgnitionProb

case object SmallTree extends ForestItem:
  val canIgnite = true
  val burnTime = SimulationConstants.smallTreeBurnTime
  val ignitionProbability = SimulationConstants.smallTreeIgnitionProb

case object GrowingTree extends ForestItem:
  val canIgnite = true
  val burnTime = SimulationConstants.growingTreeBurnTime
  val ignitionProbability = SimulationConstants.growingTreeIgnitionProb

case object Tree extends ForestItem:
  val canIgnite = true
  val burnTime = SimulationConstants.treeBurnTime
  val ignitionProbability = SimulationConstants.treeIgnitionProb

case class OnFire(originalItem: ForestItem, timeLeft: Int) extends ForestItem:
  val canIgnite = false
  val burnTime = 0
  val ignitionProbability = 0.0

case object Destroyed extends ForestItem:
  val canIgnite = false
  val burnTime = 0
  val ignitionProbability = 0.0

case class ForestConfig(
                         water: Double = 0.05,
                         rock: Double = 0.03,
                         grass: Double = 0.10,
                         bush: Double = 0.15,
                         smallTree: Double = 0.12,
                         growingTree: Double = 0.2,
                         tree: Double = 0.3
                       ) {
  val total = water + rock + grass + bush + smallTree + growingTree + tree
  require(math.abs(total - 1.0) < 0.001, s"Percentages must sum to 1.0, got $total")
}

case class ForestGrid(
                       cells: List[List[ForestItem]],
                       width: Int,
                       height: Int,
                       timeStep: Int,
                       globalEnvironment: GlobalEnvironment,
                       wind: WindDirection
                     ):

  def getCell(x: Int, y: Int): Option[ForestItem] =
    if y >= 0 && y < height && x >= 0 && x < width then
      Some(cells(y)(x))
    else
      None

  val directionalOffsets = List(
    ((0, -1), WindDirection.North),
    ((1, 0), WindDirection.East),
    ((0, 1), WindDirection.South),
    ((-1, 0), WindDirection.West)
  )

  def getBurningNeighborDirections(x: Int, y: Int): List[WindDirection] =
    directionalOffsets.flatMap { case ((dx, dy), dir) =>
      getCell(x + dx, y + dy) match
        case Some(_: OnFire) => Some(dir)
        case _ => None
    }

  // 8 cells around
  def getNeighbors(x: Int, y: Int): List[ForestItem] =
    val directions = List(
      (-1, -1), (-1, 0), (-1, 1),
      ( 0, -1),          ( 0, 1),
      ( 1, -1), ( 1, 0), ( 1, 1)
    )
    directions.flatMap { case (dx, dy) => getCell(x + dx, y + dy) }

  def countBurningNeighbors(x: Int, y: Int): Int =
    val neighbors = getNeighbors(x, y)
    if neighbors.isEmpty then 0
    else
      neighbors.map {
        case _: OnFire => 1
        case _ => 0
      }.reduce(_ + _)

  def countWaterNeighbors(x: Int, y: Int): Int =
    val neighbors = getNeighbors(x, y)
    if neighbors.isEmpty then 0
    else
      neighbors.map {
        case Water => 1
        case _ => 0
      }.reduce(_ + _)

  def calculateIgnitionProbability(x: Int, y: Int, item: ForestItem): Double =
    val burningNeighbors = countBurningNeighbors(x, y)
    val burningDirections = getBurningNeighborDirections(x, y)
    val windBonus = if burningDirections.contains(wind) then 1.5 else 1.0

    val spreadIgnition = if burningNeighbors == 0 then 0.0
    else
      val baseProb = item.ignitionProbability
      val neighborEffect = burningNeighbors * SimulationConstants.neighborIgnitionEffect
      val temperatureEffect = math.max(
        SimulationConstants.minTemperatureEffect,
        globalEnvironment.temperature / SimulationConstants.temperatureIgnitionBase
      )
      val humidityEffect = math.max(
        SimulationConstants.minHumidityEffect,
        1.0 - globalEnvironment.atmosphereHumidity
      )
      baseProb * neighborEffect * temperatureEffect * humidityEffect * windBonus

    val selfIgnition = calculateSelfIgnitionProbability(item)
    math.min(1.0, math.max(spreadIgnition, selfIgnition))

  def calculateSelfIgnitionProbability(item: ForestItem): Double =
    if !item.canIgnite then 0.0
    else
      val baseChance = SimulationConstants.baseSelfIgnitionChance
      val temperatureEffect = math.pow(
        globalEnvironment.temperature / SimulationConstants.temperatureIgnitionBase,
        SimulationConstants.temperatureSelfIgnitionMultiplier
      )
      val humidityReduction = 1.0 - (globalEnvironment.atmosphereHumidity * SimulationConstants.humiditySelfIgnitionReduction)
      val vegetationMultiplier = item match
        case Grass => 1.5
        case Bush => 1.2
        case SmallTree => 1.0
        case GrowingTree => 0.8
        case Tree => 0.6
        case _ => 1.0
      baseChance * temperatureEffect * humidityReduction * vegetationMultiplier

  def calculateExtinguishProbability(x: Int, y: Int): Double =
    val baseExtinguishProb = SimulationConstants.baseExtinguishProbability
    val waterNeighbors = countWaterNeighbors(x, y)
    val waterEffect = waterNeighbors * SimulationConstants.waterExtinguishBonus
    val humidityEffect = globalEnvironment.atmosphereHumidity * SimulationConstants.humidityExtinguishBonus
    math.min(SimulationConstants.maxExtinguishProbability, baseExtinguishProb + waterEffect + humidityEffect)

  def updateSingleCell(x: Int, y: Int): ForestItem =
    val currentItem = cells(y)(x)
    currentItem match
      case OnFire(original, timeLeft) if timeLeft <= 1 => Destroyed
      case OnFire(original, timeLeft) =>
        val extinguishProb = calculateExtinguishProbability(x, y)
        if Random.nextDouble() < extinguishProb then original
        else OnFire(original, timeLeft - 1)
      case item if item.canIgnite =>
        val ignitionProb = calculateIgnitionProbability(x, y, item)
        if Random.nextDouble() < ignitionProb then OnFire(item, item.burnTime)
        else item
      case Grass =>
        val rand = Random.nextDouble()
        rand match
          case r if r < SimulationConstants.grassToBushChance => Bush
          case r if r < SimulationConstants.grassToBushChance + SimulationConstants.grassToSmallTreeChance => SmallTree
          case r if r < SimulationConstants.grassToBushChance + SimulationConstants.grassToSmallTreeChance + SimulationConstants.grassToTreeGrowthChance => GrowingTree
          case _ => Grass
      case Bush =>
        if Random.nextDouble() < SimulationConstants.bushToSmallTreeChance then SmallTree
        else Bush
      case SmallTree =>
        if Random.nextDouble() < SimulationConstants.smallTreeToGrowingTreeChance then GrowingTree
        else SmallTree
      case GrowingTree =>
        if Random.nextDouble() < SimulationConstants.growingTreeToTreeChance then Tree
        else GrowingTree
      case Destroyed =>
        val rand = Random.nextDouble()
        rand match
          case r if r < SimulationConstants.destroyedToGrassChance => Grass
          case r if r < SimulationConstants.destroyedToGrassChance + SimulationConstants.destroyedToBushChance => Bush
          case r if r < SimulationConstants.destroyedToGrassChance + SimulationConstants.destroyedToBushChance + SimulationConstants.destroyedToSmallTreeChance => SmallTree
          case r if r < SimulationConstants.destroyedToGrassChance + SimulationConstants.destroyedToBushChance + SimulationConstants.destroyedToSmallTreeChance + SimulationConstants.destroyedToTreeChance => GrowingTree
          case _ => Destroyed
      case item => item

  def updateGrid(): ForestGrid =
    val newCells = cells.zipWithIndex.map { case (row, y) =>
      row.zipWithIndex.map { case (cell, x) =>
        updateSingleCell(x, y)
      }
    }
    copy(cells = newCells, timeStep = timeStep + 1)

  // For the terminal TODO verify emoji
  def displayGrid(): Unit =
    println(s"Time Step: ${timeStep}")
    println(s"Temperature: ${globalEnvironment.temperature}°C, Humidity: ${(globalEnvironment.atmosphereHumidity * 100).toInt}%")
    cells.foreach { row =>
      println(row.map {
        case Water => "\uD83D\uDFE6"      // Blue square
        case Rock => "⛰\uFE0F"
        case Grass => "🟩"
        case Bush => "\uD83C\uDF3F"         // Bush emoji
        case SmallTree => "🌳"
        case GrowingTree => "🌲"
        case Tree => "🌴"
        case OnFire(_, _) => "🔥"
        case Destroyed => "⬛"
      }.mkString(" "))
    }
    println()

object ForestGrid:
  def createGrid(width: Int, height: Int, config: ForestConfig, globalEnv: GlobalEnvironment): ForestGrid =
    def selectCellType(rand: Double): ForestItem =
      val selections = List(
        (config.water, Water),
        (config.rock, Rock),
        (config.grass, Grass),
        (config.bush, Bush),
        (config.smallTree, SmallTree),
        (config.growingTree, GrowingTree),
        (1.0, Tree)
      )
      val cumulative = selections.scanLeft((0.0, Water: ForestItem))((acc, curr) => (acc._1 + curr._1, curr._2))
      cumulative.find(_._1 > rand).map(_._2).getOrElse(Tree)

    val cells = (0 until height).toList.map { y =>
      (0 until width).toList.map { x =>
        selectCellType(Random.nextDouble())
      }
    }
    ForestGrid(cells, width, height, 0, globalEnv, WindDirection.East)

  def igniteAt(grid: ForestGrid, x: Int, y: Int): ForestGrid =
    if x >= 0 && x < grid.width && y >= 0 && y < grid.height then
      val currentItem = grid.cells(y)(x)
      if currentItem.canIgnite then
        val newRow = grid.cells(y).updated(x, OnFire(currentItem, currentItem.burnTime))
        val newCells = grid.cells.updated(y, newRow)
        grid.copy(cells = newCells)
      else grid
    else grid


// for data vis
object CSVExporter:
  def itemToCode(item: ForestItem): Int = item match
    case Water => 0
    case Rock => 1
    case Grass => 2
    case Bush => 3
    case SmallTree => 4
    case GrowingTree => 5
    case Tree => 6
    case OnFire(_, _) => 7
    case Destroyed => 8

  def exportSimulation(history: List[ForestGrid], filename: String): Unit =
    import java.io.PrintWriter
    import java.io.File

    val pw = new PrintWriter(new File(filename))
    pw.println("step,x,y,cell_type,temperature,humidity")

    history.foreach { grid =>
      for
        y <- grid.cells.indices
        x <- grid.cells(y).indices
      do
        val cell = grid.cells(y)(x)
        val cellCode = itemToCode(cell)
        pw.println(s"${grid.timeStep},$x,$y,$cellCode,${grid.globalEnvironment.temperature},${grid.globalEnvironment.atmosphereHumidity}")
    }
    pw.close()

// main simulation part
object SimpleForestFireSimulation:
  def runSimulation(grid: ForestGrid, maxSteps: Int): List[ForestGrid] =
    def loop(currentGrid: ForestGrid, history: List[ForestGrid]): List[ForestGrid] =
      if currentGrid.timeStep >= maxSteps then
        (currentGrid :: history).reverse
      else
        val nextGrid = currentGrid.updateGrid()
        loop(nextGrid, currentGrid :: history)
    loop(grid, List.empty)

  def main(args: Array[String]): Unit =
    Random.setSeed(12345L)

    // TODO test with manu values here
    val globalEnvironment = GlobalEnvironment(temperature = 35.0, atmosphereHumidity = 0.4)
    val config = ForestConfig(water = 0.05, rock = 0.02, grass = 0.35, bush = 0.25, smallTree = 0.15, growingTree = 0.13, tree = 0.05)

    val gridSize = 40
    val maxSteps = 50
    val initialGrid = ForestGrid.createGrid(gridSize, gridSize, config, globalEnvironment)

    println("Initial Grid:")
    initialGrid.displayGrid()

    val gridWithFires = ForestGrid.igniteAt(
      ForestGrid.igniteAt(
        ForestGrid.igniteAt(initialGrid, gridSize / 2, gridSize / 2),
        2, 2
      ),
      gridSize - 2, gridSize - 2
    )

    println("Grid with fires started:")
    gridWithFires.displayGrid()

    val simulationHistory = runSimulation(gridWithFires, maxSteps)

    println("Simulation steps:")
    simulationHistory.foreach { grid =>
      grid.displayGrid()
      Thread.sleep(500) // Small delay to see the progression
    }

    CSVExporter.exportSimulation(simulationHistory, "forest_fire_simulation.csv")
    println("Simulation exported to: forest_fire_simulation.csv")